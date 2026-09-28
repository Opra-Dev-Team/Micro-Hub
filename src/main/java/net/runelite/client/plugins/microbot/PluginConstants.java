package net.runelite.client.plugins.microbot;

/**
 * Compile-time constants shared by every plugin jar.
 * The [Opra] prefix is inlined into each @PluginDescriptor.
 */
public final class PluginConstants
{
	private PluginConstants()
	{
		throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
	}

	public static final String OPRA = "<html>[<font color=#32CD32>OP</font><font color=#FF0000>RA</font>] ";
	public static final boolean DEFAULT_ENABLED = false;
	public static final boolean IS_EXTERNAL = true;
}
