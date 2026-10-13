package com.avenoxstudio.economyshop.command;

import com.avenoxstudio.economyshop.EconomyShop;
import com.avenoxstudio.economyshop.gui.MenuHolder;
import com.avenoxstudio.economyshop.model.BuyOrder;
import com.avenoxstudio.economyshop.storage.OrderRepository;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final EconomyShop plugin;
    private final Map<String, YamlConfiguration> configCache = new HashMap<>();
    public ShopCommand(EconomyShop plugin){this.plugin=plugin; reloadFiles();}

    public void reloadFiles(){
        configCache.clear();
        for(String name:new String[]{"messages.yml","categories.yml","prices.yml","gui.yml","storage.yml","orders.yml","sounds.yml","permissions.yml","integrations.yml","tax.yml","auction.yml"}){
            File file=new File(plugin.getDataFolder(),name);
            if(file.isFile())configCache.put(name,YamlConfiguration.loadConfiguration(file));
        }
    }
    private YamlConfiguration yaml(String name){return configCache.computeIfAbsent(name,n->YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),n)));}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
    private String msg(String key){return color(yaml("messages.yml").getString("prefix","&8[&aEconomyShop&8] &r")+yaml("messages.yml").getString(key,key));}
    private void tell(CommandSender s,String key){s.sendMessage(msg(key));}
    private String money(BigDecimal n){int scale=Math.max(0,Math.min(2,plugin.getConfig().getInt("decimal-places",2)));return plugin.getConfig().getString("currency-symbol","$")+n.setScale(scale,RoundingMode.HALF_UP).toPlainString();}
    private BigDecimal price(Material m,String key){
        YamlConfiguration prices=yaml("prices.yml");String path="items."+m.name()+"."+key;
        if(!prices.isSet(path))return null;
        try{BigDecimal value=new BigDecimal(String.valueOf(prices.get(path)));if(value.signum()<=0)return null;int scale=Math.max(0,Math.min(2,plugin.getConfig().getInt("decimal-places",2)));BigDecimal normalized=value.setScale(scale,RoundingMode.HALF_UP);return normalized.signum()<=0?null:normalized;}
        catch(NumberFormatException ex){plugin.getLogger().warning("Invalid price at prices.yml "+path+"; disabling this price.");return null;}
    }
    private boolean enabled(Material m,String key){return yaml("prices.yml").getBoolean("items."+m.name()+"."+key+"-enabled",false);}
    private ItemStack item(Material m,String name,String... lore){
        ItemStack it=new ItemStack(m);ItemMeta meta=it.getItemMeta();meta.setDisplayName(color(name));
        if(lore.length>0)meta.setLore(Arrays.stream(lore).map(this::color).toList());it.setItemMeta(meta);return it;
    }
    private void open(Player p,String type,String arg,String title,int size){MenuHolder holder=new MenuHolder(type,arg);Inventory inv=Bukkit.createInventory(holder,size,color(title));holder.bind(inv);p.openInventory(inv);}
    private void filler(Inventory inv){Material mat=Material.matchMaterial(yaml("gui.yml").getString("filler","GRAY_STAINED_GLASS_PANE"));if(mat==null)mat=Material.GRAY_STAINED_GLASS_PANE;ItemStack f=item(mat," ");for(int i=0;i<inv.getSize();i++)if(inv.getItem(i)==null)inv.setItem(i,f);}
    public void openShop(Player p){openShop(p,0);}
    public void openShop(Player p,int page){
        YamlConfiguration cats=yaml("categories.yml");ConfigurationSection sec=cats.getConfigurationSection("categories");
        if(sec==null){p.sendMessage(color("&cNo shop categories are configured."));return;}
        List<String> ids=new ArrayList<>(sec.getKeys(false));int maxPage=Math.max(0,(ids.size()-1)/45);page=Math.max(0,Math.min(page,maxPage));
        open(p,"shop",Integer.toString(page),yaml("gui.yml").getString("titles.shop","&8EconomyShop"),54);Inventory inv=p.getOpenInventory().getTopInventory();int start=page*45;
        for(int i=0;i<45&&start+i<ids.size();i++){
            String id=ids.get(start+i),path="categories."+id;Material icon=Material.matchMaterial(cats.getString(path+".icon","CHEST"));if(icon==null||icon.isAir())icon=Material.CHEST;
            List<String> names=cats.getStringList(path+".items");long available=names.stream().map(Material::matchMaterial).filter(Objects::nonNull).filter(m->enabled(m,"buy")&&price(m,"buy")!=null).count();
            inv.setItem(i,item(icon,cats.getString(path+".name",id),"&7Configured items: &f"+names.size(),"&7Available to buy: &a"+available,"&eClick to browse"));
        }
        fillerExcept(inv);if(page>0)inv.setItem(45,item(Material.ARROW,"&ePrevious page"));inv.setItem(47,item(Material.COMPASS,"&bSearch shop","&7Use &f/shop search <item>"));
        inv.setItem(49,item(Material.GOLD_INGOT,"&6Your balance",plugin.hasEconomy()&&plugin.economy().hasAccount(p)?"&7Balance: &e"+money(BigDecimal.valueOf(plugin.economy().getBalance(p))):"&cEconomy unavailable"));
        if(page<maxPage)inv.setItem(51,item(Material.ARROW,"&eNext page"));inv.setItem(53,item(Material.BARRIER,"&cClose"));
    }
    private void fillerExcept(Inventory inv){Material mat=Material.matchMaterial(yaml("gui.yml").getString("filler","GRAY_STAINED_GLASS_PANE"));if(mat==null||mat.isAir())mat=Material.GRAY_STAINED_GLASS_PANE;ItemStack f=item(mat,"&7 ");for(int i=0;i<inv.getSize();i++)if(inv.getItem(i)==null)inv.setItem(i,f);}
    public void openCategory(Player p,String id,int page){
        YamlConfiguration cats=yaml("categories.yml");String path="categories."+id;
        if(!cats.isConfigurationSection(path)){p.sendMessage("Unknown category.");return;}
        List<String> materials=cats.getStringList(path+".items").stream().filter(x->{Material m=Material.matchMaterial(x);return m!=null&&enabled(m,"buy")&&price(m,"buy")!=null;}).toList();
        page=Math.max(0,Math.min(page,Math.max(0,(materials.size()-1)/45)));
        open(p,"category",id+";"+page,yaml("gui.yml").getString("titles.category","&8Shop: %category%").replace("%category%",cats.getString(path+".name",id)),54);
        Inventory inv=p.getOpenInventory().getTopInventory();int start=page*45;
        for(int i=0;i<45&&start+i<materials.size();i++){Material m=Material.matchMaterial(materials.get(start+i));BigDecimal b=price(m,"buy"),s=price(m,"sell");inv.setItem(i,item(m,"&f"+pretty(m),"&7Buy: &e"+money(b)+" &7each",s==null?"&7Sell: &cNot configured":"&7Sell: &a"+money(s)+" &7each","&eLeft-click: buy 1","&eRight-click: choose stack quantity"));}
        if(page>0)inv.setItem(45,item(Material.ARROW,"&ePrevious page"));inv.setItem(49,item(Material.BARRIER,"&cBack to categories"));
        if(start+45<materials.size())inv.setItem(53,item(Material.ARROW,"&eNext page"));fillerExcept(inv);
        if(page>0)inv.setItem(45,item(Material.ARROW,"&ePrevious page"));inv.setItem(49,item(Material.BARRIER,"&cBack to categories"));if(start+45<materials.size())inv.setItem(53,item(Material.ARROW,"&eNext page"));
    }
    public void openSearch(Player p,String query,int page){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        if(q.isBlank()){p.sendMessage(color("&cUsage: /shop search <item>"));return;}
        if(q.length()>48){p.sendMessage(color("&cSearch is limited to 48 characters."));return;}
        List<Material> matches=new ArrayList<>();ConfigurationSection section=yaml("categories.yml").getConfigurationSection("categories");
        if(section!=null)for(String id:section.getKeys(false))for(String raw:yaml("categories.yml").getStringList("categories."+id+".items")){
            Material m=Material.matchMaterial(raw);
            if(m!=null&&enabled(m,"buy")&&price(m,"buy")!=null&&(m.name().toLowerCase(Locale.ROOT).contains(q)||pretty(m).toLowerCase(Locale.ROOT).contains(q))&&!matches.contains(m))matches.add(m);
        }
        matches.sort(Comparator.comparing(this::pretty,String.CASE_INSENSITIVE_ORDER));
        int maxPage=Math.max(0,(matches.size()-1)/45);page=Math.max(0,Math.min(page,maxPage));
        open(p,"search",q+";"+page,color("&8Search: &7"+q),54);Inventory inv=p.getOpenInventory().getTopInventory();int start=page*45;
        for(int i=0;i<45&&start+i<matches.size();i++){Material m=matches.get(start+i);BigDecimal b=price(m,"buy"),sell=price(m,"sell");inv.setItem(i,item(m,"&f"+pretty(m),"&7Buy: &e"+money(b)+" &7each",sell==null?"&7Sell: &cNot configured":"&7Sell: &a"+money(sell)+" &7each","&eLeft-click: buy 1","&eRight-click: select quantity"));}
        if(matches.isEmpty())inv.setItem(22,item(Material.BARRIER,"&cNo results","&7Try another search term."));
        if(page>0)inv.setItem(45,item(Material.ARROW,"&ePrevious page"));inv.setItem(49,item(Material.OAK_DOOR,"&eBack to shop"));if(page<maxPage)inv.setItem(53,item(Material.ARROW,"&eNext page"));
        fillerExcept(inv);
        for(int i=0;i<45&&start+i<matches.size();i++){Material m=matches.get(start+i);BigDecimal b=price(m,"buy"),sell=price(m,"sell");inv.setItem(i,item(m,"&f"+pretty(m),"&7Buy: &e"+money(b)+" &7each",sell==null?"&7Sell: &cNot configured":"&7Sell: &a"+money(sell)+" &7each","&eLeft-click: buy 1","&eRight-click: select quantity"));}
        if(matches.isEmpty())inv.setItem(22,item(Material.BARRIER,"&cNo results","&7Try another search term."));if(page>0)inv.setItem(45,item(Material.ARROW,"&ePrevious page"));inv.setItem(49,item(Material.OAK_DOOR,"&eBack to shop"));if(page<maxPage)inv.setItem(53,item(Material.ARROW,"&eNext page"));
    }
    private void openQuantity(Player p,Material m,int qty,String returnType,String returnArg){
        int max=(int)Math.max(1,Math.min(2304,plugin.getConfig().getLong("max-purchase-quantity",2304)));qty=Math.max(1,Math.min(qty,max));
        open(p,"quantity",m.name()+";"+qty+";"+returnType+";"+returnArg,yaml("gui.yml").getString("titles.quantity","&8Select Quantity"),27);Inventory inv=p.getOpenInventory().getTopInventory();BigDecimal unit=price(m,"buy");
        inv.setItem(10,item(Material.RED_STAINED_GLASS_PANE,"&c-16"));inv.setItem(11,item(Material.REDSTONE,"&c-1"));inv.setItem(13,item(m,"&f"+pretty(m),"&7Quantity: &e"+qty,"&7Unit price: &e"+money(unit),"&7Total: &6"+money(unit.multiply(BigDecimal.valueOf(qty)).setScale(2,RoundingMode.HALF_UP))));inv.setItem(15,item(Material.EMERALD,"&a+1"));inv.setItem(16,item(Material.LIME_STAINED_GLASS_PANE,"&a+16"));inv.setItem(21,item(Material.BARRIER,"&cCancel"));inv.setItem(23,item(Material.LIME_CONCRETE,"&aConfirm purchase"));
        fillerExcept(inv);inv.setItem(10,item(Material.RED_STAINED_GLASS_PANE,"&c-16"));inv.setItem(11,item(Material.REDSTONE,"&c-1"));inv.setItem(13,item(m,"&f"+pretty(m),"&7Quantity: &e"+qty,"&7Unit price: &e"+money(unit),"&7Total: &6"+money(unit.multiply(BigDecimal.valueOf(qty)).setScale(2,RoundingMode.HALF_UP))));inv.setItem(15,item(Material.EMERALD,"&a+1"));inv.setItem(16,item(Material.LIME_STAINED_GLASS_PANE,"&a+16"));inv.setItem(21,item(Material.BARRIER,"&cCancel"));inv.setItem(23,item(Material.LIME_CONCRETE,"&aConfirm purchase"));
    }
    private String pretty(Material m){String[] words=m.name().toLowerCase(Locale.ROOT).split("_");return Arrays.stream(words).map(w->w.isEmpty()?w:Character.toUpperCase(w.charAt(0))+w.substring(1)).collect(Collectors.joining(" "));}
    public void openOrders(Player p){openOrders(p,null,0);}
    public void openOrders(Player p,String materialFilter){openOrders(p,materialFilter,0);}
    private void openOrders(Player p,String materialFilter,int page){
        if(plugin.orders()==null){tell(p,"safe-failure");return;}
        String filter=materialFilter==null?null:Optional.ofNullable(Material.matchMaterial(materialFilter.toUpperCase(Locale.ROOT))).map(Material::name).orElse("__NO_MATCH__");
        page=Math.max(0,page);open(p,"orders",(filter==null?"":filter)+";"+page,yaml("gui.yml").getString("titles.orders","&8Player Buy Orders"),54);Inventory inv=p.getOpenInventory().getTopInventory();
        try{
            List<BuyOrder> list=plugin.orders().activeOrders(filter,46,page*45);boolean hasNext=list.size()>45;if(hasNext)list=list.subList(0,45);int slot=0;
            for(BuyOrder o:list){Material m=Material.matchMaterial(o.material());if(m==null)continue;OfflinePlayer owner=Bukkit.getOfflinePlayer(o.owner());inv.setItem(slot++,item(m,"&eOrder #"+o.id()+" &7"+pretty(m),"&7Owner: &f"+(owner.getName()==null?o.owner().toString().substring(0,8):owner.getName()),"&7Remaining: &f"+o.remainingQuantity()+" / "+o.originalQuantity(),"&7Price each: &a"+money(o.unitPrice()),"&7Escrow: &e"+money(o.escrow()),"&eClick to fulfill from your inventory"));}
            if(list.isEmpty())inv.setItem(22,item(Material.BARRIER,"&cNo active orders","&7Try another material or check back later."));
            fillerExcept(inv);if(page>0)inv.setItem(45,item(Material.ARROW,"&ePrevious page"));inv.setItem(49,item(Material.EMERALD,"&aCreate order","&7Use: /order create <material> <quantity> <unit-price>"));if(hasNext)inv.setItem(51,item(Material.ARROW,"&eNext page"));inv.setItem(53,item(Material.BARRIER,"&cClose"));
        }catch(SQLException e){plugin.getLogger().warning("Order list failed: "+e.getMessage());inv.setItem(22,item(Material.BARRIER,"&cCould not load orders","&7Check the server log."));fillerExcept(inv);inv.setItem(49,item(Material.EMERALD,"&aCreate order"));inv.setItem(53,item(Material.BARRIER,"&cClose"));}
    }
    public void openSell(Player p){
        open(p,"sell","",yaml("gui.yml").getString("titles.sell","&8Sell Items"),54);Inventory inv=p.getOpenInventory().getTopInventory();
        int slot=0;for(ItemStack stack:p.getInventory().getStorageContents()){if(stack==null||stack.getType().isAir()||!plainPricedItem(stack)||!enabled(stack.getType(),"sell")||price(stack.getType(),"sell")==null)continue;
            Material m=stack.getType();inv.setItem(slot++,item(m,"&f"+pretty(m),"&7In inventory: &f"+stack.getAmount(),"&7Base sell: &a"+money(price(m,"sell"))+" each","&eClick to sell eligible items of this type"));if(slot>=45)break;}
        inv.setItem(49,item(Material.GOLD_INGOT,"&aSell all eligible items","&eClick to sell"));inv.setItem(53,item(Material.BARRIER,"&cClose"));fillerExcept(inv);inv.setItem(49,item(Material.GOLD_INGOT,"&aSell all eligible items","&eClick to sell"));
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String c=command.getName().toLowerCase(Locale.ROOT);
        if(c.equals("economyshop")){
            if(args.length==0){sender.sendMessage(color("&aEconomyShop &7- /shop, /sell, /orders, /order create, /order manage"));return true;}
            if(args[0].equalsIgnoreCase("reload")&&sender.hasPermission("economyshop.admin.reload")){plugin.reloadPlugin();tell(sender,"reload-success");return true;}
            if(args[0].equalsIgnoreCase("admin")&&sender.hasPermission("economyshop.admin")){sender.sendMessage(color("&aEconomyShop admin: /economyshop reload | /economyshop diagnostics"));return true;}
            if(args[0].equalsIgnoreCase("diagnostics")&&sender.hasPermission("economyshop.admin.diagnostics")){
                sender.sendMessage(color("&6EconomyShop diagnostics"));sender.sendMessage(color("&7Economy provider: "+(plugin.hasEconomy()?plugin.economy().getName():"&cUnavailable")));sender.sendMessage(color("&7SQLite storage: "+(plugin.orders()!=null?"&aReady":"&cUnavailable")));
                if(plugin.orders()!=null)try{List<String> rows=plugin.orders().recentAudit(10);if(rows.isEmpty())sender.sendMessage(color("&7No recent audit entries."));else rows.forEach(row->sender.sendMessage(color("&8"+row)));}catch(SQLException ex){sender.sendMessage(color("&cCould not read audit log."));}
                return true;
            }
            tell(sender,"no-permission");return true;
        }
        if(c.equals("balance")){if(!(sender instanceof Player p)){tell(sender,"player-only");return true;}if(!plugin.hasEconomy()){tell(p,"economy-unavailable");return true;}p.sendMessage(color("&aBalance: &e"+money(BigDecimal.valueOf(plugin.economy().getBalance(p)))));return true;}
        if(c.equals("shop")){if(!(sender instanceof Player p)){tell(sender,"player-only");return true;}if(args.length>=2&&args[0].equalsIgnoreCase("search")){openSearch(p,String.join(" ",Arrays.copyOfRange(args,1,args.length)),0);return true;}if(args.length==1){openCategory(p,args[0].toLowerCase(Locale.ROOT),0);return true;}openShop(p);return true;}
        if(c.equals("sell")){
            if(!(sender instanceof Player p)){tell(sender,"player-only");return true;}
            if(args.length==0){openSell(p);return true;}
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "hand" -> {ItemStack hand=p.getInventory().getItemInMainHand();if(hand.getType().isAir()){tell(p,"no-items");return true;}sellMaterial(p,hand.getType(),hand.getAmount(),true);return true;}
                case "all" -> {sellAll(p);return true;}
                case "history" -> {showSellHistory(p);return true;}
                case "preview" -> {showSellPreview(p);return true;}
                default -> {openSell(p);return true;}
            }
        }
        if(c.equals("orders")){if(sender instanceof Player p){openOrders(p,args.length>0?String.join("_",args):null);}else tell(sender,"player-only");return true;}
        if(c.equals("sellhand")){if(!(sender instanceof Player p)){tell(sender,"player-only");return true;}ItemStack hand=p.getInventory().getItemInMainHand();if(hand.getType().isAir()){tell(p,"no-items");return true;}sellMaterial(p,hand.getType(),hand.getAmount(),true);return true;}
        if(c.equals("sellall")){if(!(sender instanceof Player p)){tell(sender,"player-only");return true;}sellAll(p);return true;}
        if(c.equals("worth")){if(!(sender instanceof Player p)){tell(sender,"player-only");return true;}p.sendMessage(color("&eConfigured prices are listed in /shop. Values are not guaranteed order payouts."));return true;}
        if(c.equals("sellhistory")){if(!(sender instanceof Player p)){tell(sender,"player-only");return true;}showSellHistory(p);return true;}
        if(c.equals("order")){
            if(!(sender instanceof Player p)){tell(sender,"player-only");return true;}
            if(args.length==0||args[0].equalsIgnoreCase("list")){openOrders(p);return true;}
            if(args[0].equalsIgnoreCase("manage")){manageOrders(p);return true;}
            if(args[0].equalsIgnoreCase("claim")){claim(p);return true;}
            if(args[0].equalsIgnoreCase("create")){createOrder(p,args);return true;}
            if(args[0].equalsIgnoreCase("cancel")&&args.length==2){cancelOrder(p,args[1]);return true;}
            if(args[0].equalsIgnoreCase("fulfill")&&args.length>=2){fulfillOrder(p,args[1],args.length>=3?args[2]:"all");return true;}
            if(!args[0].equalsIgnoreCase("list")){openOrders(p,String.join("_",args));return true;}
            p.sendMessage(color("&e/order create <material> <quantity> <unit-price> | manage | cancel <id> | fulfill <id> [quantity|all] | claim"));return true;
        }
        return true;
    }
    private void showSellHistory(Player p){
        if(plugin.orders()==null){tell(p,"safe-failure");return;}
        try{List<String> rows=plugin.orders().history(p.getUniqueId(),10);p.sendMessage(color("&6Recent sales:"));if(rows.isEmpty())p.sendMessage(color("&7No sales recorded."));else rows.forEach(row->p.sendMessage(color("&7"+row)));}
        catch(SQLException ex){tell(p,"safe-failure");}
    }
    private void showSellPreview(Player p){
        Map<Material,Long> quantities=new LinkedHashMap<>();for(ItemStack stack:p.getInventory().getStorageContents())if(plainPricedItem(stack)&&enabled(stack.getType(),"sell")&&price(stack.getType(),"sell")!=null)quantities.merge(stack.getType(),(long)stack.getAmount(),Long::sum);
        if(quantities.isEmpty()){tell(p,"no-items");return;}BigDecimal estimate=BigDecimal.ZERO;long totalItems=0;
        for(var entry:quantities.entrySet()){BigDecimal unit=price(entry.getKey(),"sell");estimate=estimate.add(unit.multiply(BigDecimal.valueOf(entry.getValue())));totalItems+=entry.getValue();}
        p.sendMessage(color("&6Sell preview &8- &f"+totalItems+" items"));for(var entry:quantities.entrySet())p.sendMessage(color("&7"+entry.getValue()+"x "+pretty(entry.getKey())+" &8@ &a"+money(price(entry.getKey(),"sell"))+" each"));
        p.sendMessage(color("&7Estimated server-price total: &e"+money(estimate.setScale(2,RoundingMode.HALF_UP))));p.sendMessage(color("&8Player orders may pay more. Use /sell all to sell."));
    }
    private void createOrder(Player p,String[] args){
        if(!p.hasPermission("economyshop.orders.create")){tell(p,"no-permission");return;}
        if(!plugin.hasEconomy()){tell(p,"economy-unavailable");return;}
        if(plugin.orders()==null){tell(p,"safe-failure");return;}
        if(args.length<4){p.sendMessage(color("&e/order create <material> <quantity> <unit-price>"));return;}
        Material m=Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));long qty;BigDecimal unit;
        try{qty=Long.parseLong(args[2]);unit=new BigDecimal(args[3]).setScale(2,RoundingMode.HALF_UP);}catch(Exception e){tell(p,"invalid-amount");return;}
        long max=plugin.getConfig().getLong("orders.max-quantity",23040);BigDecimal min=new BigDecimal(plugin.getConfig().getString("orders.min-unit-price","0.01"));BigDecimal maxPrice=new BigDecimal(plugin.getConfig().getString("orders.max-unit-price","1000000"));
        if(m==null||m.isAir()||m.isItem() == false||qty<1||qty>max||unit.signum()<=0||unit.compareTo(min)<0||unit.compareTo(maxPrice)>0){tell(p,"invalid-amount");return;}
        BigDecimal total=unit.multiply(BigDecimal.valueOf(qty)).setScale(2,RoundingMode.HALF_UP);
        if(total.compareTo(BigDecimal.valueOf(plugin.economy().getBalance(p)))>0){tell(p,"insufficient-funds");return;}
        try{if(plugin.orders().activeOwnerOrders(p.getUniqueId()).size()>=plugin.getConfig().getInt("orders.max-active-per-player",10)){p.sendMessage(color("&cYou have reached the active order limit."));return;}}catch(SQLException e){tell(p,"safe-failure");return;}
        EconomyResponse withdraw=plugin.economy().withdrawPlayer(p,total.doubleValue());if(!withdraw.transactionSuccess()){tell(p,"insufficient-funds");return;}
        try{long expiry=System.currentTimeMillis()+plugin.getConfig().getLong("orders.expiry-hours",168)*3600000L;long id=plugin.orders().create(p.getUniqueId(),m.name(),qty,unit,total,expiry);p.sendMessage(color("&aCreated order #"+id+" for "+qty+" "+pretty(m)+" at "+money(unit)+" each. Escrow: "+money(total)));}catch(Exception e){EconomyResponse refund=plugin.economy().depositPlayer(p,total.doubleValue());if(!refund.transactionSuccess()){plugin.getLogger().severe("RECONCILIATION REQUIRED: failed to refund order escrow to "+p.getUniqueId()+" amount "+total);try{plugin.orders().recordAudit(p.getUniqueId(),"RECONCILIATION_REQUIRED","order-create refund failed; amount="+total+"; material="+m.name()+"; quantity="+qty);}catch(SQLException auditError){plugin.getLogger().severe("Could not persist reconciliation audit: "+auditError.getMessage());}}plugin.getLogger().warning("Order create failed: "+e.getMessage());tell(p,"safe-failure");}
    }
    private void cancelOrder(Player p,String raw){
        if(!plugin.hasEconomy()||plugin.orders()==null){tell(p,"economy-unavailable");return;}
        try{long id=Long.parseLong(raw);Optional<BuyOrder> found=plugin.orders().get(id);if(found.isEmpty()||!found.get().owner().equals(p.getUniqueId())||!"ACTIVE".equals(found.get().status())){tell(p,"order-not-found");return;}
            BuyOrder o=found.get();if(plugin.orders().cancel(id,p.getUniqueId())){EconomyResponse r=plugin.economy().depositPlayer(p,o.escrow().doubleValue());if(r.transactionSuccess())p.sendMessage(color("&aCancelled order #"+id+". Refund: "+money(o.escrow())));else{plugin.getLogger().severe("RECONCILIATION REQUIRED: order #"+id+" cancelled but refund failed for "+p.getUniqueId()+" amount "+o.escrow());plugin.orders().recordAudit(p.getUniqueId(),"RECONCILIATION_REQUIRED","order-cancel refund failed; order="+id+"; amount="+o.escrow());p.sendMessage(color("&cOrder cancelled but refund needs admin reconciliation. Order #"+id));}}
            else tell(p,"order-not-found");
        }catch(Exception e){tell(p,"safe-failure");}
    }
    private void manageOrders(Player p){if(plugin.orders()==null){tell(p,"safe-failure");return;}try{p.sendMessage(color("&6Your active orders:"));for(BuyOrder o:plugin.orders().ownerOrders(p.getUniqueId()))p.sendMessage(color("&e#"+o.id()+" &f"+o.remainingQuantity()+"x "+o.material()+" &7@ "+money(o.unitPrice())+" each, escrow "+money(o.escrow())+" &8(cancel: /order cancel "+o.id()+")"));}catch(SQLException e){tell(p,"safe-failure");}}
    private void claim(Player p){
        if(plugin.orders()==null){tell(p,"safe-failure");return;}
        ItemStack[] before=null;boolean inventoryChanged=false;
        try{
            List<OrderRepository.Delivery> deliveries=plugin.orders().pendingDeliveries(p.getUniqueId());
            if(deliveries.isEmpty()){p.sendMessage(color("&7You have no pending order deliveries."));return;}
            long requiredEmptySlots=0;
            for(OrderRepository.Delivery delivery:deliveries){Material material=Material.matchMaterial(delivery.material());if(material==null){plugin.getLogger().warning("Invalid pending delivery material: "+delivery.material());continue;}requiredEmptySlots+=(delivery.quantity()+material.getMaxStackSize()-1)/material.getMaxStackSize();}
            long emptySlots=Arrays.stream(p.getInventory().getStorageContents()).filter(stack->stack==null||stack.getType().isAir()).count();
            if(requiredEmptySlots>emptySlots){p.sendMessage(color("&cMake room in your inventory first. Your deliveries remain pending."));return;}
            before=p.getInventory().getStorageContents().clone();List<Long> acknowledgements=new ArrayList<>();int delivered=0;
            for(OrderRepository.Delivery delivery:deliveries){
                Material material=Material.matchMaterial(delivery.material());if(material==null)continue;long remaining=delivery.quantity();
                while(remaining>0){int amount=(int)Math.min(remaining,material.getMaxStackSize());Map<Integer,ItemStack> overflow=p.getInventory().addItem(new ItemStack(material,amount));
                    if(!overflow.isEmpty()){p.getInventory().setStorageContents(before);inventoryChanged=false;p.sendMessage(color("&cDelivery could not fit. Your pending deliveries were left unchanged."));return;}
                    remaining-=amount;delivered+=amount;
                }
                acknowledgements.add(delivery.id());
            }
            inventoryChanged=true;
            plugin.orders().acknowledgeDeliveries(p.getUniqueId(),acknowledgements);
            inventoryChanged=false;
            p.sendMessage(color("&aClaimed "+delivered+" item(s)."));
        }catch(SQLException ex){
            if(inventoryChanged&&before!=null)p.getInventory().setStorageContents(before);
            plugin.getLogger().log(java.util.logging.Level.WARNING,"Order delivery claim failed",ex);tell(p,"safe-failure");
        }
    }
    private void fulfillOrder(Player p,String raw,String qtyRaw){
        if(!plugin.hasEconomy()||plugin.orders()==null){tell(p,"economy-unavailable");return;}
        try{long id=Long.parseLong(raw);Optional<BuyOrder> f=plugin.orders().get(id);if(f.isEmpty()){tell(p,"order-not-found");return;}BuyOrder o=f.get();if(o.owner().equals(p.getUniqueId())&&!plugin.getConfig().getBoolean("orders.allow-self-fulfillment",false)){p.sendMessage(color("&cYou cannot fulfill your own order."));return;}
            Material m=Material.matchMaterial(o.material());if(m==null){tell(p,"safe-failure");return;}long have=countEligible(p,m),qty=qtyRaw.equalsIgnoreCase("all")?Math.min(have,o.remainingQuantity()):Long.parseLong(qtyRaw);qty=Math.min(qty,Math.min(have,o.remainingQuantity()));if(qty<1){tell(p,"no-items");return;}
            BigDecimal payout=o.unitPrice().multiply(BigDecimal.valueOf(qty)).setScale(2,RoundingMode.HALF_UP);if(payout.compareTo(o.escrow())>0){tell(p,"safe-failure");return;}
            EconomyResponse paid=plugin.economy().depositPlayer(p,payout.doubleValue());if(!paid.transactionSuccess()){tell(p,"safe-failure");return;}
            if(!plugin.orders().fulfill(id,p.getUniqueId(),qty,payout)){EconomyResponse rollback=plugin.economy().withdrawPlayer(p,payout.doubleValue());if(!rollback.transactionSuccess()){plugin.getLogger().severe("RECONCILIATION REQUIRED: order payout rollback failed for "+p.getUniqueId()+" amount "+payout+" order="+id);plugin.orders().recordAudit(p.getUniqueId(),"RECONCILIATION_REQUIRED","fulfillment rollback failed; order="+id+"; amount="+payout);}p.sendMessage(color("&cOrder changed. Refresh the order list."));return;}
            remove(p,m,qty);p.sendMessage(color("&aFulfilled "+qty+" "+pretty(m)+" for "+money(payout)+". The owner can claim the items with /order claim."));
        }catch(Exception e){plugin.getLogger().warning("Order fulfillment error: "+e.getMessage());tell(p,"safe-failure");}
    }
    private boolean plainPricedItem(ItemStack stack){return stack!=null&&!stack.getType().isAir()&&!stack.hasItemMeta();}
    private long countEligible(Player p,Material m){long n=0;for(ItemStack i:p.getInventory().getStorageContents())if(i!=null&&i.getType()==m&&plainPricedItem(i))n+=i.getAmount();return n;}
    private long count(Player p,Material m){long n=0;for(ItemStack i:p.getInventory().getStorageContents())if(i!=null&&i.getType()==m)n+=i.getAmount();return n;}
    private void remove(Player p,Material m,long amount){long left=amount;for(int slot=0;slot<p.getInventory().getStorageContents().length&&left>0;slot++){ItemStack i=p.getInventory().getItem(slot);if(i==null||i.getType()!=m||!plainPricedItem(i))continue;int take=(int)Math.min(left,i.getAmount());i.setAmount(i.getAmount()-take);left-=take;if(i.getAmount()<=0)p.getInventory().setItem(slot,null);}}
    public void sellMaterial(Player p,Material m,long amount,boolean handOnly){
        if(!plugin.hasEconomy()){tell(p,"economy-unavailable");return;}if(!enabled(m,"sell")||price(m,"sell")==null){tell(p,"not-sellable");return;}
        long available=handOnly?((p.getInventory().getItemInMainHand().getType()==m&&plainPricedItem(p.getInventory().getItemInMainHand()))?p.getInventory().getItemInMainHand().getAmount():0):countEligible(p,m);long qty=Math.min(amount,available);if(qty<1){tell(p,"no-items");return;}
        BigDecimal base=price(m,"sell");long left=qty;BigDecimal total=BigDecimal.ZERO;
        if(plugin.orders()!=null){
            try{for(BuyOrder o:plugin.orders().activeOrders(m.name(),100)){if(left<=0)break;if(o.owner().equals(p.getUniqueId())&&!plugin.getConfig().getBoolean("orders.allow-self-fulfillment",false))continue;if(o.unitPrice().compareTo(base)<=0)continue;long take=Math.min(left,o.remainingQuantity());take=Math.min(take,o.escrow().divide(o.unitPrice(),0,RoundingMode.DOWN).longValue());if(take<=0)continue;BigDecimal payout=o.unitPrice().multiply(BigDecimal.valueOf(take)).setScale(2,RoundingMode.HALF_UP);EconomyResponse paid=plugin.economy().depositPlayer(p,payout.doubleValue());if(!paid.transactionSuccess())break;if(!plugin.orders().fulfill(o.id(),p.getUniqueId(),take,payout)){EconomyResponse rollback=plugin.economy().withdrawPlayer(p,payout.doubleValue());if(!rollback.transactionSuccess()){plugin.getLogger().severe("RECONCILIATION REQUIRED: order route rollback failed for "+p.getUniqueId()+" amount "+payout+" order="+o.id());plugin.orders().recordAudit(p.getUniqueId(),"RECONCILIATION_REQUIRED","sell-route rollback failed; order="+o.id()+"; amount="+payout);}continue;}total=total.add(payout);left-=take;}}
            catch(SQLException e){plugin.getLogger().warning("Order routing failed: "+e.getMessage());}
        }
        if(left>0){BigDecimal payout=base.multiply(BigDecimal.valueOf(left)).setScale(2,RoundingMode.HALF_UP);EconomyResponse r=plugin.economy().depositPlayer(p,payout.doubleValue());if(r.transactionSuccess()){total=total.add(payout);try{plugin.orders().recordBaseSale(p.getUniqueId(),m.name(),left,base,payout);}catch(Exception ignored){}}else{left=qty-(qty-left);}}
        long sold=qty-left; if(sold>0)removeAmount(p,m,sold,handOnly);p.sendMessage(color("&aSold "+sold+" "+pretty(m)+" for "+money(total)+"."));
    }
    private void removeAmount(Player p,Material m,long qty,boolean handOnly){if(handOnly){ItemStack hand=p.getInventory().getItemInMainHand();if(hand.getType()==m&&plainPricedItem(hand)){int take=(int)Math.min(qty,hand.getAmount());hand.setAmount(hand.getAmount()-take);if(hand.getAmount()<=0)p.getInventory().setItemInMainHand(null);}}else remove(p,m,qty);}
    private void sellAll(Player p){Map<Material,Long> quantities=new LinkedHashMap<>();for(ItemStack i:p.getInventory().getStorageContents())if(i!=null&&!i.getType().isAir()&&plainPricedItem(i)&&enabled(i.getType(),"sell")&&price(i.getType(),"sell")!=null)quantities.merge(i.getType(),(long)i.getAmount(),Long::sum);if(quantities.isEmpty()){tell(p,"no-items");return;}for(var e:quantities.entrySet())sellMaterial(p,e.getKey(),e.getValue(),false);}
    public void onMenuClick(Player p,MenuHolder h,int slot,org.bukkit.event.inventory.ClickType click,ItemStack clicked){
        if(slot<0||slot>=p.getOpenInventory().getTopInventory().getSize())return;
        switch(h.type()){
            case "shop" -> {int page=0;try{page=Integer.parseInt(h.argument());}catch(NumberFormatException ignored){}if(slot==53){p.closeInventory();return;}if(slot==45&&page>0){openShop(p,page-1);return;}if(slot==47){p.closeInventory();p.sendMessage(color("&eSearch the shop with: &f/shop search <item>"));return;}if(slot==51){openShop(p,page+1);return;}if(slot>=45)return;ConfigurationSection section=yaml("categories.yml").getConfigurationSection("categories");if(section==null)return;List<String> ids=new ArrayList<>(section.getKeys(false));int index=page*45+slot;if(index>=0&&index<ids.size())openCategory(p,ids.get(index),0);}
            case "category" -> {String[] a=h.argument().split(";");String id=a[0];int page=a.length>1?Integer.parseInt(a[1]):0;if(slot==49){openShop(p);return;}if(slot==45&&page>0){openCategory(p,id,page-1);return;}if(slot==53){openCategory(p,id,page+1);return;}if(clicked==null||clicked.getType().isAir()||slot>=45)return;Material m=clicked.getType();if(!enabled(m,"buy")||price(m,"buy")==null)return;if(click.isRightClick()){openQuantity(p,m,Math.min(m.getMaxStackSize(),plugin.getConfig().getInt("default-purchase-quantity",1)),"category",h.argument());return;}purchase(p,m,click.isShiftClick()?Math.min(64,plugin.getConfig().getLong("max-purchase-quantity",2304)):1);}
            case "search" -> {String[] a=h.argument().split(";",2);String q=a[0];int page=a.length>1?Integer.parseInt(a[1]):0;if(slot==49){openShop(p);return;}if(slot==45&&page>0){openSearch(p,q,page-1);return;}if(slot==53){openSearch(p,q,page+1);return;}if(clicked==null||clicked.getType().isAir()||slot>=45)return;Material m=clicked.getType();if(!enabled(m,"buy")||price(m,"buy")==null)return;if(click.isRightClick()){openQuantity(p,m,Math.min(m.getMaxStackSize(),plugin.getConfig().getInt("default-purchase-quantity",1)),"search",h.argument());return;}purchase(p,m,click.isShiftClick()?64:1);}
            case "quantity" -> {String[] a=h.argument().split(";",4);if(a.length<4)return;Material m=Material.matchMaterial(a[0]);if(m==null||!enabled(m,"buy")||price(m,"buy")==null)return;int qty;try{qty=Integer.parseInt(a[1]);}catch(NumberFormatException ex){return;}int max=(int)Math.max(1,Math.min(2304,plugin.getConfig().getLong("max-purchase-quantity",2304)));String ret=a[2],retArg=a[3];if(slot==21){if(ret.equals("search")){String[] r=retArg.split(";",2);openSearch(p,r[0],r.length>1?Integer.parseInt(r[1]):0);}else{String[] r=retArg.split(";",2);openCategory(p,r[0],r.length>1?Integer.parseInt(r[1]):0);}return;}if(slot==10)qty=Math.max(1,qty-16);else if(slot==11)qty=Math.max(1,qty-1);else if(slot==15)qty=Math.min(max,qty+1);else if(slot==16)qty=Math.min(max,qty+16);else if(slot==23){purchase(p,m,qty);if(ret.equals("search")){String[] r=retArg.split(";",2);openSearch(p,r[0],r.length>1?Integer.parseInt(r[1]):0);}else{String[] r=retArg.split(";",2);openCategory(p,r[0],r.length>1?Integer.parseInt(r[1]):0);}return;}else return;openQuantity(p,m,qty,ret,retArg);}
            case "sell" -> {if(slot==53){p.closeInventory();return;}if(slot==49){sellAll(p);openSell(p);return;}if(slot>=45||clicked==null||clicked.getType().isAir())return;sellMaterial(p,clicked.getType(),countEligible(p,clicked.getType()),false);openSell(p);}
            case "orders" -> {String[] state=h.argument().split(";",2);String filter=state.length>0&&!state[0].isBlank()?state[0]:null;int page=state.length>1?Integer.parseInt(state[1]):0;if(slot==53){p.closeInventory();return;}if(slot==45&&page>0){openOrders(p,filter,page-1);return;}if(slot==51){openOrders(p,filter,page+1);return;}if(slot==49){p.closeInventory();p.sendMessage(color("&eUse /order create <material> <quantity> <unit-price>"));return;}if(clicked!=null&&clicked.hasItemMeta()&&clicked.getItemMeta().hasDisplayName()){String dn=ChatColor.stripColor(clicked.getItemMeta().getDisplayName());if(dn.startsWith("Order #")){String id=dn.substring(7).split(" ")[0];p.closeInventory();p.sendMessage(color("&eUse /order fulfill "+id+" all to fulfill from your inventory."));}}}
        }
    }
    private void purchase(Player p,Material m,long qty){
        if(!plugin.hasEconomy()){tell(p,"economy-unavailable");return;}qty=Math.max(1,Math.min(qty,plugin.getConfig().getLong("max-purchase-quantity",2304)));BigDecimal unit=price(m,"buy");if(unit==null||unit.signum()<=0){tell(p,"safe-failure");return;}BigDecimal total=unit.multiply(BigDecimal.valueOf(qty)).setScale(2,RoundingMode.HALF_UP);if(total.compareTo(BigDecimal.valueOf(plugin.economy().getBalance(p)))>0){tell(p,"insufficient-funds");return;}
        long capacity=0;for(ItemStack i:p.getInventory().getStorageContents()){if(i==null||i.getType().isAir())capacity+=m.getMaxStackSize();else if(i.isSimilar(new ItemStack(m)))capacity+=m.getMaxStackSize()-i.getAmount();}
        if(capacity<qty){tell(p,"inventory-full");return;}EconomyResponse r=plugin.economy().withdrawPlayer(p,total.doubleValue());if(!r.transactionSuccess()){tell(p,"insufficient-funds");return;}
        ItemStack[] before=p.getInventory().getStorageContents().clone();
        long left=qty;while(left>0){int n=(int)Math.min(left,m.getMaxStackSize());Map<Integer,ItemStack> overflow=p.getInventory().addItem(new ItemStack(m,n));if(!overflow.isEmpty()){
            p.getInventory().setStorageContents(before);EconomyResponse refund=plugin.economy().depositPlayer(p,total.doubleValue());
            if(!refund.transactionSuccess()){plugin.getLogger().severe("RECONCILIATION REQUIRED: purchase refund failed for "+p.getUniqueId()+" amount "+total);if(plugin.orders()!=null)try{plugin.orders().recordAudit(p.getUniqueId(),"RECONCILIATION_REQUIRED","purchase rollback failed; material="+m.name()+"; quantity="+qty+"; amount="+total);}catch(SQLException auditError){plugin.getLogger().severe("Could not persist reconciliation audit: "+auditError.getMessage());}}
            tell(p,"safe-failure");return;}left-=n;}
        p.sendMessage(color("&aPurchased "+qty+" "+pretty(m)+" for "+money(total)+"."));p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_YES,0.7f,1.2f);
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(command.getName().equalsIgnoreCase("shop")&&args.length==1)return Arrays.asList("search").stream().filter(v->v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(command.getName().equalsIgnoreCase("order")&&args.length==1)return Arrays.asList("create","manage","cancel","fulfill","claim","list").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(command.getName().equalsIgnoreCase("economyshop")&&args.length==1)return Arrays.asList("reload","admin","diagnostics").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return Collections.emptyList();
    }
}