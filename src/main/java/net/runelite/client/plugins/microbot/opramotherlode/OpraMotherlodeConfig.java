package net.runelite.client.plugins.microbot.opramotherlode;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.opramotherlode.enums.MLMMiningSpotList;

@ConfigGroup(OpraMotherlodeConfig.configGroup)
@ConfigInformation(
	"• This plugin will automate mining in motherload mine <br />" +
	"• If using deposit all feature, <b>ensure you lock the slots you wish to keep in inventory</b> <br />" +
	"• Start near the bank chest in motherload mine <br />"
)
public interface OpraMotherlodeConfig extends Config
{
	String configGroup = "opra-motherlode";

	String useDepositAll = "useDepositAll";
	String antiCrash = "antiCrash";
	String dropGems = "dropGems";
	String useUpstairsMine = "useUpstairsMine";
	String useUpstairsHopper = "useUpstairsHopper";
	String fixWaterwheel = "fixWaterwheel";
	String miningArea = "miningArea";

	@ConfigSection(
		name = "General",
		description = "General Plugin Settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Features",
		description = "Feature Settings",
		position = 1
	)
	String featureSection = "features";

	@ConfigItem(
		keyName = useDepositAll,
		name = "Use Deposit All",
		description = "Clicks Deposit inventory in the deposit box. Lock the slots you want to keep. When off, pay-dirt is deposited and the pickaxe, hammer, and gem bag stay.",
		position = 0,
		section = generalSection
	)
	default boolean useDepositAll()
	{
		return false;
	}

	@ConfigItem(
		keyName = antiCrash,
		name = "Anti Crash",
		description = "Skips rocks other players are mining, on both floors",
		position = 1,
		section = generalSection
	)
	default boolean useAntiCrash()
	{
		return false;
	}

	@ConfigItem(
		keyName = dropGems,
		name = "Drop Gems",
		description = "Automatically drop gems while mining",
		position = 2,
		section = generalSection
	)
	default boolean dropGems()
	{
		return false;
	}

	// Mine upstairs
	@ConfigItem(
		keyName = useUpstairsMine,
		name = "Use Mine Upstairs",
		description = "When Mining Area is Any, use the upper level. A specific Mining Area is used as selected.",
		position = 0,
		section = featureSection
	)
	default boolean mineUpstairs()
	{
		return false;
	}

	// Upstairs hopper unlocked
	@ConfigItem(
		keyName = useUpstairsHopper,
		name = "Use Upstairs Hopper",
		description = "Deposit into the upstairs hopper while you are on the upper level",
		position = 1,
		section = featureSection
	)
	default boolean upstairsHopperUnlocked()
	{
		return false;
	}

	@ConfigItem(
		keyName = fixWaterwheel,
		name = "Fix Water Wheel",
		description = "Repair a water wheel when it is stopped. A spinning wheel is left alone.",
		position = 2,
		section = featureSection
	)
	default boolean fixWaterwheel()
	{
		return true;
	}

	// Mining Area Selection
	@ConfigItem(
		keyName = miningArea,
		name = "Mining Area",
		description = "Choose the specific area to mine in Motherload Mine",
		position = 3,
		section = featureSection
	)
	default MLMMiningSpotList miningArea()
	{
		return MLMMiningSpotList.ANY;
	}
}
