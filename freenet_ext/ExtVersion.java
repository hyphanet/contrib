package freenet.node;

/**
 * Central spot for stuff related to the versioning of freenet-ext.
 */
public abstract class ExtVersion {

	/** The build number of the current revision */
	private static final int buildNumber = 8;

	public static final int buildNumber() {
		return buildNumber;
	}
	
	/** Revision number of Version.java as read from CVS */
	public static final String cvsRevision = "@custom@";
}
