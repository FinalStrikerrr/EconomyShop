package com.avenoxstudio.economyshop.storage;

import com.avenoxstudio.economyshop.model.BuyOrder;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class OrderRepository implements AutoCloseable {
    private final Connection connection;
    public OrderRepository(File file) throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new SQLException("Could not create plugin data directory");
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA busy_timeout=5000");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS orders (id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, material TEXT NOT NULL, original_qty INTEGER NOT NULL, remaining_qty INTEGER NOT NULL, unit_price TEXT NOT NULL, escrow TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, status TEXT NOT NULL)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_orders_active_material ON orders(status, material, unit_price)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sales (id INTEGER PRIMARY KEY AUTOINCREMENT, seller TEXT NOT NULL, material TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price TEXT NOT NULL, total TEXT NOT NULL, destination TEXT NOT NULL, order_id INTEGER, created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS deliveries (id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, material TEXT NOT NULL, quantity INTEGER NOT NULL, order_id INTEGER NOT NULL, claimed INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS audit (id INTEGER PRIMARY KEY AUTOINCREMENT, actor TEXT NOT NULL, action TEXT NOT NULL, details TEXT NOT NULL, created_at INTEGER NOT NULL)");
        }
    }
    public synchronized long create(UUID owner, String material, long quantity, BigDecimal unitPrice, BigDecimal escrow, long expiresAt) throws SQLException {
        if(owner==null||material==null||material.isBlank()||quantity<1||unitPrice==null||unitPrice.signum()<=0||escrow==null||escrow.signum()<0||expiresAt<=System.currentTimeMillis())
            throw new IllegalArgumentException("Invalid buy order data");
        BigDecimal expectedEscrow=unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2,java.math.RoundingMode.HALF_UP);
        if(expectedEscrow.compareTo(escrow)!=0)throw new IllegalArgumentException("Escrow does not match order quantity and unit price");
        connection.setAutoCommit(false);
        try (PreparedStatement p = connection.prepareStatement("INSERT INTO orders(owner,material,original_qty,remaining_qty,unit_price,escrow,created_at,expires_at,status) VALUES(?,?,?,?,?,?,?,?, 'ACTIVE')", Statement.RETURN_GENERATED_KEYS)) {
            long now=System.currentTimeMillis();
            p.setString(1,owner.toString()); p.setString(2,material); p.setLong(3,quantity); p.setLong(4,quantity);
            p.setString(5,unitPrice.toPlainString()); p.setString(6,escrow.toPlainString()); p.setLong(7,now); p.setLong(8,expiresAt); p.executeUpdate();
            try (ResultSet r=p.getGeneratedKeys()) { if (!r.next()) throw new SQLException("No order ID generated"); long id=r.getLong(1); connection.commit(); return id; }
        } catch (SQLException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }
    public synchronized List<BuyOrder> activeOrders(String material, int limit) throws SQLException { return activeOrders(material,limit,0); }
    public synchronized List<BuyOrder> activeOrders(String material, int limit, int offset) throws SQLException {
        String sql = "SELECT * FROM orders WHERE status='ACTIVE' AND expires_at>? " + (material == null ? "" : "AND material=? ") + "ORDER BY CAST(unit_price AS REAL) DESC, id ASC LIMIT ? OFFSET ?";
        try (PreparedStatement p=connection.prepareStatement(sql)) {
            int i=1;p.setLong(i++,System.currentTimeMillis());if(material!=null)p.setString(i++,material);p.setInt(i,Math.max(1,Math.min(limit,500)));p.setInt(i+1,Math.max(0,offset));
            try(ResultSet r=p.executeQuery()){List<BuyOrder> out=new ArrayList<>();while(r.next())out.add(map(r));return out;}
        }
    }
    public synchronized List<BuyOrder> ownerOrders(UUID owner) throws SQLException {
        try(PreparedStatement p=connection.prepareStatement("SELECT * FROM orders WHERE owner=? AND status='ACTIVE' ORDER BY id DESC")) {
            p.setString(1,owner.toString()); try(ResultSet r=p.executeQuery()){List<BuyOrder> out=new ArrayList<>();while(r.next())out.add(map(r));return out;}
        }
    }
    public synchronized List<BuyOrder> activeOwnerOrders(UUID owner) throws SQLException {
        try(PreparedStatement p=connection.prepareStatement("SELECT * FROM orders WHERE owner=? AND status='ACTIVE' AND expires_at>? ORDER BY id DESC")) {
            p.setString(1,owner.toString());p.setLong(2,System.currentTimeMillis());
            try(ResultSet r=p.executeQuery()){List<BuyOrder> out=new ArrayList<>();while(r.next())out.add(map(r));return out;}
        }
    }
    public synchronized Optional<BuyOrder> get(long id) throws SQLException {
        try(PreparedStatement p=connection.prepareStatement("SELECT * FROM orders WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}
    }
    public synchronized boolean cancel(long id, UUID owner) throws SQLException {
        try(PreparedStatement p=connection.prepareStatement("UPDATE orders SET status='CANCELLED' WHERE id=? AND owner=? AND status='ACTIVE'")){p.setLong(1,id);p.setString(2,owner.toString());return p.executeUpdate()==1;}
    }
    public synchronized boolean fulfill(long id, UUID seller, long quantity, BigDecimal payout) throws SQLException {
        connection.setAutoCommit(false);
        try {
            BuyOrder order;
            try(PreparedStatement p=connection.prepareStatement("SELECT * FROM orders WHERE id=? AND status='ACTIVE' AND expires_at>?")) {
                p.setLong(1,id);p.setLong(2,System.currentTimeMillis());try(ResultSet r=p.executeQuery()){if(!r.next()){connection.rollback();return false;}order=map(r);}
            }
            if(quantity<1||quantity>order.remainingQuantity()||payout.signum()<0||payout.compareTo(order.escrow())>0){connection.rollback();return false;}
            BigDecimal expectedPayout=order.unitPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2,java.math.RoundingMode.HALF_UP);
            if(expectedPayout.compareTo(payout)!=0){connection.rollback();return false;}
            long remaining=order.remainingQuantity()-quantity;
            BigDecimal escrow=order.escrow().subtract(payout);
            String status=remaining==0?"COMPLETED":"ACTIVE";
            try(PreparedStatement p=connection.prepareStatement("UPDATE orders SET remaining_qty=?,escrow=?,status=? WHERE id=? AND status='ACTIVE' AND remaining_qty=? AND escrow=?")) {
                p.setLong(1,remaining);p.setString(2,escrow.toPlainString());p.setString(3,status);p.setLong(4,id);p.setLong(5,order.remainingQuantity());p.setString(6,order.escrow().toPlainString());
                if(p.executeUpdate()!=1){connection.rollback();return false;}
            }
            try(PreparedStatement p=connection.prepareStatement("INSERT INTO deliveries(owner,material,quantity,order_id,created_at) VALUES(?,?,?,?,?)")){
                p.setString(1,order.owner().toString());p.setString(2,order.material());p.setLong(3,quantity);p.setLong(4,id);p.setLong(5,System.currentTimeMillis());p.executeUpdate();
            }
            try(PreparedStatement p=connection.prepareStatement("INSERT INTO sales(seller,material,quantity,unit_price,total,destination,order_id,created_at) VALUES(?,?,?,?,?,'ORDER',?,?)")){
                p.setString(1,seller.toString());p.setString(2,order.material());p.setLong(3,quantity);p.setString(4,order.unitPrice().toPlainString());p.setString(5,payout.toPlainString());p.setLong(6,id);p.setLong(7,System.currentTimeMillis());p.executeUpdate();
            }
            connection.commit(); return true;
        } catch(SQLException e){connection.rollback();throw e;} finally {connection.setAutoCommit(true);}
    }
    public synchronized void recordBaseSale(UUID seller, String material, long quantity, BigDecimal unitPrice, BigDecimal total) throws SQLException {
        try(PreparedStatement p=connection.prepareStatement("INSERT INTO sales(seller,material,quantity,unit_price,total,destination,created_at) VALUES(?,?,?,?,?,'SERVER',?)")){
            p.setString(1,seller.toString());p.setString(2,material);p.setLong(3,quantity);p.setString(4,unitPrice.toPlainString());p.setString(5,total.toPlainString());p.setLong(6,System.currentTimeMillis());p.executeUpdate();
        }
    }
    public synchronized List<String> history(UUID seller, int limit) throws SQLException {
        try(PreparedStatement p=connection.prepareStatement("SELECT material,quantity,total,destination,created_at FROM sales WHERE seller=? ORDER BY id DESC LIMIT ?")){
            p.setString(1,seller.toString());p.setInt(2,Math.min(50,Math.max(1,limit)));try(ResultSet r=p.executeQuery()){List<String> out=new ArrayList<>();while(r.next())out.add(new java.util.Date(r.getLong(5))+" | "+r.getString(2)+"x "+r.getString(1)+" | $"+r.getString(3)+" | "+r.getString(4));return out;}
        }
    }

    public record Delivery(long id, String material, long quantity) {}

    /** Reads pending deliveries without consuming them. The caller acknowledges only after inventory insertion succeeds. */
    public synchronized List<Delivery> pendingDeliveries(UUID owner) throws SQLException {
        try (PreparedStatement p=connection.prepareStatement("SELECT id,material,quantity FROM deliveries WHERE owner=? AND claimed=0 ORDER BY id ASC")) {
            p.setString(1, owner.toString());
            try (ResultSet r=p.executeQuery()) {
                List<Delivery> out=new ArrayList<>();
                while(r.next()) out.add(new Delivery(r.getLong("id"), r.getString("material"), r.getLong("quantity")));
                return out;
            }
        }
    }

    /** Acknowledges only the selected pending deliveries, in one database transaction. */
    public synchronized void acknowledgeDeliveries(UUID owner, List<Long> ids) throws SQLException {
        if(ids==null||ids.isEmpty()) return;
        connection.setAutoCommit(false);
        try (PreparedStatement p=connection.prepareStatement("UPDATE deliveries SET claimed=1 WHERE id=? AND owner=? AND claimed=0")) {
            for(Long id:ids) { if(id==null||id<1) continue; p.setLong(1,id); p.setString(2,owner.toString()); p.addBatch(); }
            p.executeBatch(); connection.commit();
        } catch(SQLException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }

    public synchronized void recordAudit(UUID actor,String action,String details)throws SQLException{
        try(PreparedStatement p=connection.prepareStatement("INSERT INTO audit(actor,action,details,created_at) VALUES(?,?,?,?)")){
            p.setString(1,actor==null?"CONSOLE":actor.toString());p.setString(2,action);p.setString(3,details==null?"":details);p.setLong(4,System.currentTimeMillis());p.executeUpdate();
        }
    }

    public synchronized List<String> recentAudit(int limit)throws SQLException{
        try(PreparedStatement p=connection.prepareStatement("SELECT actor,action,details,created_at FROM audit ORDER BY id DESC LIMIT ?")){
            p.setInt(1,Math.max(1,Math.min(100,limit)));try(ResultSet r=p.executeQuery()){List<String> out=new ArrayList<>();while(r.next())out.add(new java.util.Date(r.getLong(4))+" | "+r.getString(2)+" | "+r.getString(1)+" | "+r.getString(3));return out;}
        }
    }

    private BuyOrder map(ResultSet r)throws SQLException{return new BuyOrder(r.getLong("id"),UUID.fromString(r.getString("owner")),r.getString("material"),r.getLong("original_qty"),r.getLong("remaining_qty"),new BigDecimal(r.getString("unit_price")),new BigDecimal(r.getString("escrow")),r.getLong("created_at"),r.getLong("expires_at"),r.getString("status"));}
    @Override public synchronized void close() throws SQLException {connection.close();}
}