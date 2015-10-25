package freenet.node;

/**
 * Central spot for stuff related to the versioning of freenet-ext.
 */
public abstract class ExtVersion {

	/** The build number of the current revision */
	public static final int buildNumber = 50; // This is the freenet-ext.jar build the node was built with

	public static final int buildNumber() {
		return -42;  // This value indicates that the freenet-ext.jar the node is using at runtime has extBuildNumber() and extRevisionNumber() methods
	}

	public static final int extBuildNumber() {  // This returns the freenet-ext.jar build the node is using at runtime
		return buildNumber;
	}
	
	/** Revision number of ExtVersion.java as read from SVN */
	public static final String cvsRevision = "@custom@";  // This is the freenet-ext.jar revision the node was built with

	public static final String cvsRevision() {  // This method is here for backwards compatability with a few node testing builds between 999 and 1000
		return cvsRevision;
	}

	public static final String extRevisionNumber() {  // This returns the freenet-ext.jar revision the node is using at runtime
		return cvsRevision;
	}
}
