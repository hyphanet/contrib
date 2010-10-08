package com.onionnetworks.util;

import java.io.*;
import java.util.*;
import java.net.URL;

/**
 * This class is used for deploying native libraries that are stored inside
 * jar files.
 *
 * For each jar that contains native libraries there must be a file called 
 * "lib/native.properties" that has a format similar to the following:
 * <pre>
 * com.onionnetworks.native.keys=fec8-linux-x86,fec16-linux-x86,fec8-win32,fec16-win32
 *
 * com.onionnetworks.native.fec8-linux-x86.name=fec8
 * com.onionnetworks.native.fec8-linux-x86.osarch=linux-x86
 * com.onionnetworks.native.fec8-linux-x86.path=lib/linux/x86/libfec8.so
 *
 * com.onionnetworks.native.fec16-linux-x86.name=fec16
 * com.onionnetworks.native.fec16-linux-x86.osarch=linux-x86
 * com.onionnetworks.native.fec16-linux-x86.path=lib/linux/x86/libfec16.so
 * 
 * com.onionnetworks.native.fec8-win32.name=fec8
 * com.onionnetworks.native.fec8-win32.osarch=win32
 * com.onionnetworks.native.fec8-win32.path=lib/win32/fec8.dll
 * 
 * com.onionnetworks.native.fec16-win32.name=fec16
 * com.onionnetworks.native.fec16-win32.osarch=win32
 * com.onionnetworks.native.fec16-win32.path=lib/win32/fec16.dll
 * </pre>
 * 
 *
 * For the "osarch" property note that Sun's VM uses 'i386' and IBM's uses 
 * 'x86' so we convert all to 'x86'.
 * For now, we map 'Windows 95', 'Windows 98', 'Windows NT', and 
 * 'Windows 2000', no matter what the architecture, all to 'win32'. 
 * Depending on what native libraries are added in the future, this may have 
 * to be made more flexible; for example, if a library depends on Windows 2000
 * features or is tuned for Pentium processors.
 * We will just the "os.name" and "os.arch" properties to retrieve this 
 * information on all other systems not explicitly mentioned above.
 *
 *
 * @author Justin F. Chapweske
 *
 */
public class NativeDeployer {


	public final static String OS_ARCH;
	static {
		final String OS = System.getProperty("os.name").startsWith("Windows ") ? "win32" : System.getProperty("os.name").toLowerCase();
		if(System.getProperty("os.arch").toLowerCase().matches("(i?[x0-9]86_64|amd64)"))
			OS_ARCH=OS+"-x86_64";
		else if(System.getProperty("os.arch").toLowerCase().indexOf("86") != -1)
			OS_ARCH=OS+"-x86";
		else
			OS_ARCH=OS+"-"+System.getProperty("os.arch").toLowerCase();
		System.out.println("Attempting to deploy Native FEC for " + OS_ARCH);
	}

	public final static String NATIVE_PROPERTIES_PATH = "lib/native.properties";

	public synchronized final static String getLibraryPath(ClassLoader cl, String libName) {
		long t = System.currentTimeMillis();
		IOException iox = null;
		/* this code avoids try {} finally {} idiom for the sake of GCJ 3.0 */
		try {
			String libPath = (String) findLibraries(cl).get(libName);
			if (libPath == null) {
				return null;
			}
			try {
				return getLocalResourcePath(cl, libPath);
			} catch (IOException ex) {
				iox = ex;
			} 
			System.out.println("It took "+(System.currentTimeMillis()-t)+
					" millis to extract "+libName);
			if (iox != null) {
				iox.printStackTrace();
			}

		} catch (IOException e) {
			e.printStackTrace();
		} 
		return null;
	}

	// Since the local copy of the resource isn't stored in a temporary
	// directory, at some point we'll presumably implement version-based
	// caching rather than extracting the file every time the code is run....
	public synchronized final static String getLocalResourcePath
		(ClassLoader cl, String resourcePath) throws IOException {

			File f = File.createTempFile("libfec",".tmp");
			URL url = cl.getResource(resourcePath);
			if (url == null) {
				return null;
			}
			InputStream is = url.openStream();
			f.delete(); // VERY VERY important, VM crashes w/o this :P
			OutputStream os = new FileOutputStream(f);

			byte[] b = new byte[1024];
			int c;
			while ((c = is.read(b)) != -1) {
				os.write(b,0,c);
			}
			is.close();
			os.flush();
			os.close();
			return f.toString();
		}

	/**
	 * @return A HashMap mapping library names to paths for this os/arch.
	 */
	private final static HashMap findLibraries(ClassLoader cl)
		throws IOException {

			HashMap libMap = new HashMap();
			// loop through all of the properties files.
			for (Enumeration en=cl.getResources(NATIVE_PROPERTIES_PATH);
					en.hasMoreElements();){
				Properties p = new Properties();
				p.load(((URL) en.nextElement()).openStream());
				// Extract the keys and loop through all of the libs.
				for (StringTokenizer st = new StringTokenizer
						(p.getProperty("com.onionnetworks.native.keys"),",");
						st.hasMoreTokens();) {
					String key = st.nextToken().trim();
					// If it matches the os and arch then add it.
					if (p.getProperty("com.onionnetworks.native."+key+".osarch").
							trim().equals(OS_ARCH)) {

						libMap.put(p.getProperty
								("com.onionnetworks.native."+key+".name").trim(),
								p.getProperty
								("com.onionnetworks.native."+key+".path").trim());
					}
				}
			}
			return libMap;
		}
}
