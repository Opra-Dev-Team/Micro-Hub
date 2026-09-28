package net.runelite.client.plugins.microbot.banksorter;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class BankSortItem {
    static final Pattern ITEM_NAME_SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:\\s*\\((\\d+)\\))?$");

    private final int id;
    private final int classificationId;
    private final String originalName;
    private final int originalIndex;
    private final int quantity;
    private final int currentTab;
    private final String processedName;
    private final String baseName;
    private final int doseOrCharge;
    private final String category;
    private final String itemSetType;
    private final String itemTier;
    private final int itemLevel;
    private final int workflowStage;
    private final BankHomeTab homeTab;

    public BankSortItem(int id, String originalName, int originalIndex, int quantity, int currentTab) {
        this(id, originalName, originalIndex, quantity, currentTab, id, originalName);
    }

    public BankSortItem(int id, String originalName, int originalIndex, int quantity, int currentTab,
                        int classificationId, String classificationName) {
        this.id = id;
        this.classificationId = classificationId;
        this.originalName = originalName;
        this.originalIndex = originalIndex;
        this.quantity = quantity;
        this.currentTab = currentTab;

        String normalizedName = classificationName == null || classificationName.isEmpty()
                ? originalName : classificationName;
        String tempProcessedName = normalizedName.replaceAll("<col=[^>]*>(.*?)</col>", "$1").trim().toLowerCase();
        tempProcessedName = tempProcessedName.replace(" (noted)", "").replace(" (placeholder)", "");
        this.processedName = tempProcessedName;

        String tempBaseName;
        int tempDoseOrCharge = -1;
        Matcher suffixMatcher = ITEM_NAME_SUFFIX_PATTERN.matcher(this.processedName);
        if (suffixMatcher.matches()) {
            tempBaseName = suffixMatcher.group(1).trim();
            if (suffixMatcher.group(2) != null) {
                try {
                    tempDoseOrCharge = Integer.parseInt(suffixMatcher.group(2));
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            tempBaseName = this.processedName;
        }
        this.baseName = tempBaseName;
        this.doseOrCharge = tempDoseOrCharge;
        this.itemSetType = BankClassifier.determineItemSetType(this.baseName);
        this.itemTier = BankClassifier.determineItemTier(this.baseName);
        this.category = BankClassifier.classifyItem(this);
        int[] workflow = BankClassifier.getWorkflowData(this);
        this.itemLevel = workflow[0];
        this.workflowStage = workflow[1];
        this.homeTab = BankClassifier.homeTab(this);
    }
}
