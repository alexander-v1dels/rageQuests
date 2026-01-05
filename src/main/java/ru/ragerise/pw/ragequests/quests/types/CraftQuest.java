package ru.ragerise.pw.ragequests.quests.types;

import org.bukkit.Material;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import ru.ragerise.pw.ragequests.quests.Quest;
import ru.ragerise.pw.ragequests.quests.QuestType;

import java.util.List;
import java.util.Map;

public class CraftQuest extends Quest {

    private final Material material;

    public CraftQuest(int id, String name, List<String> desc, Material icon, Material material, int amount, List<ItemStack> rewards) {
        super(id, name, desc, icon, amount, rewards);
        this.material = material;
    }

    @Override
    public QuestType getType() {
        return QuestType.CRAFT;
    }

    @Override
    public boolean matchesEvent(org.bukkit.event.Event event, org.bukkit.entity.Player player) {
        if (!(event instanceof CraftItemEvent e)) return false;
        if (e.getWhoClicked() != player) return false;

        ItemStack result = e.getRecipe().getResult();
        return result != null && result.getType() == material;
    }

    @Override
    public int getIncrementAmount(org.bukkit.event.Event event) {
        if (!(event instanceof CraftItemEvent e)) return 0;

        return calculateCraftAmount(e);
    }

    private int calculateCraftAmount(CraftItemEvent e) {
        Recipe recipe = e.getRecipe();
        if (recipe == null) return 0;

        int resultAmount = recipe.getResult().getAmount(); // количество предметов за один набор ингредиентов

        if (!e.isShiftClick()) {
            return resultAmount;
        }

        // Для shift-клик — рассчитываем, сколько наборов можно скрафтить
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix().clone(); // копия, чтобы не менять оригинал

        int craftableSets = Integer.MAX_VALUE;

        if (recipe instanceof ShapelessRecipe shapeless) {
            List<ItemStack> ingredients = shapeless.getIngredientList();

            for (ItemStack ingredient : ingredients) {
                if (ingredient == null || ingredient.getType() == Material.AIR) continue;

                int available = 0;
                for (ItemStack item : matrix) {
                    if (item != null && item.isSimilar(ingredient)) {
                        available += item.getAmount();
                    }
                }

                int needed = ingredient.getAmount();
                if (needed > 0) {
                    craftableSets = Math.min(craftableSets, available / needed);
                }
            }
        } else if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, ItemStack> ingredientMap = shaped.getIngredientMap();

            for (String row : shaped.getShape()) {
                for (char key : row.toCharArray()) {
                    ItemStack ingredient = ingredientMap.get(key);
                    if (ingredient == null || ingredient.getType() == Material.AIR) continue;

                    int available = 0;
                    for (ItemStack item : matrix) {
                        if (item != null && item.isSimilar(ingredient)) {
                            available += item.getAmount();
                        }
                    }

                    int needed = ingredient.getAmount();
                    if (needed > 0) {
                        craftableSets = Math.min(craftableSets, available / needed);
                    }
                }
            }
        } else {
            // Для других рецептов (stonecutter и т.д.) просто базовое количество
            craftableSets = 1;
        }

        if (craftableSets == Integer.MAX_VALUE || craftableSets < 0) craftableSets = 0;

        return craftableSets * resultAmount;
    }
}