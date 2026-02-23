package com.smpcore.trade;

import com.smpcore.config.ConfigManager;
import com.smpcore.economy.EconomyManager;
import com.smpcore.model.CurrencyDefinition;
import com.smpcore.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TradeManager {
    private final ConfigManager config;
    private final EconomyManager economy;
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();

    public TradeManager(ConfigManager config, EconomyManager economy) {
        this.config = config;
        this.economy = economy;
    }

    public boolean enabled() {
        return config.settings().tradingEnabled();
    }

    public void request(Player sender, Player target) {
        pendingRequests.put(target.getUniqueId(), sender.getUniqueId());
        sender.sendMessage(Text.c("&aTrade request sent to " + target.getName()));
        target.sendMessage(Text.c("&e" + sender.getName() + " requested a trade. Use /trade accept"));
    }

    public boolean accept(Player target) {
        UUID requesterId = pendingRequests.remove(target.getUniqueId());
        if (requesterId == null) {
            target.sendMessage(Text.c("&cNo pending trade requests."));
            return false;
        }
        Player requester = target.getServer().getPlayer(requesterId);
        if (requester == null) {
            target.sendMessage(Text.c("&cRequester is offline."));
            return false;
        }

        ItemStack targetItem = target.getInventory().getItemInMainHand();
        ItemStack requesterItem = requester.getInventory().getItemInMainHand();
        if (targetItem.getType().isAir() || requesterItem.getType().isAir()) {
            target.sendMessage(Text.c("&cBoth players must hold an item in main hand."));
            requester.sendMessage(Text.c("&cBoth players must hold an item in main hand."));
            return false;
        }

        target.getInventory().setItemInMainHand(requesterItem.clone());
        requester.getInventory().setItemInMainHand(targetItem.clone());
        target.sendMessage(Text.c("&aTrade completed with " + requester.getName()));
        requester.sendMessage(Text.c("&aTrade completed with " + target.getName()));
        return true;
    }

    public boolean payInTrade(Player from, Player to, double amount, String currencyId) {
        CurrencyDefinition currency = config.findCurrency(currencyId);
        if (currency == null || !economy.enabled()) {
            return false;
        }
        if (!economy.withdraw(from.getUniqueId(), currency.id(), amount)) {
            return false;
        }
        economy.deposit(to.getUniqueId(), currency.id(), amount);
        from.sendMessage(Text.c("&aTrade payment sent: " + economy.format(amount, currency)));
        to.sendMessage(Text.c("&aTrade payment received: " + economy.format(amount, currency)));
        return true;
    }
}
