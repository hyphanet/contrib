/*
 *  Cachedot watches Slashdot and mirrors the sites that new stories link to into
 *  Freenet.
 *
 *  TODO:
 *  Make fcpputsite work
 *  Convert external links to used __CHECKED_HTTP__
 *  Make the index page nicer (like have a copy of the story with the links replaced)
 *  Be polite and check for robot.txt directives
 *  (Longer term): Try to remove the reliance on external Unix programs like wget and
 *  fcpputsite
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 *  Mirror the hyperlinks on a Slashdot story to Freenet
 *
 *@author     Sanity
 *@created    July 20, 2002
 */
public class Cachedot {
	/**
	 *  The main program for the Cachedot class
	 *
	 *@param  args                       The command line arguments
	 *@exception  MalformedURLException  Description of the Exception
	 *@exception  IOException            Description of the Exception
	 *@exception  InterruptedException   Description of the Exception
	 */
	static URL slashXml;
	static  URL   slashFP;
	static  File  mirrorDir  = new File("/tmp/cachedot");


	/**
	 *  The main program for the Cachedot class
	 *
	 *@param  args                       The command line arguments
	 *@exception  MalformedURLException  Description of the Exception
	 *@exception  IOException            Description of the Exception
	 *@exception  InterruptedException   Description of the Exception
	 */
	public static void main(String[] args)
		throws MalformedURLException, IOException, InterruptedException {
		slashXml = new URL("http://slashdot.org/slashdot.xml");
		slashFP = new URL("http://slashdot.org/");

		String  lastXML  = "";

		while (true) {
			// Grab slashdot XML page
			log("Checking " + slashXml + " for changes");
			InputStream   i   = slashXml.openStream();
			StringBuffer  sb  = new StringBuffer(100);
			while (true) {
				int  r  = i.read();
				if (r == -1)
					break;
				sb.append((char) r);
			}
			if (lastXML.hashCode() != sb.toString().hashCode()) {
				lastXML = sb.toString();
				Story  s  = getLastStory();
				grabStory(s);
				Util.fcpInsert(mirrorDir, s.getId());
			}

			Thread.sleep(10000);
		}
	}


	/**
	 *  Gets the newUrls attribute of the Cachedot object
	 *
	 *@return                  The lastStory value
	 *@exception  IOException  Description of the Exception
	 */
	public static Story getLastStory()
		throws IOException {
		log("Grabbing last story from " + slashFP);
		InputStream   i   = slashFP.openStream();
		StringBuffer  sb  = new StringBuffer(1000);
		while (true) {
			int  r  = i.read();
			if (r == -1)
				break;
			sb.append((char) r);
		}
		Pattern       ex  = Pattern.compile("FACE=\"arial,helvetica\" SIZE=\"4\" COLOR=\"#FFFFFF\"><B>(.*?)</B>.*?<i>(.*?)</i>.*?articles/(.*?).shtml",
				Pattern.DOTALL);
		Matcher       m   = ex.matcher(sb.toString());
		m.find();
		return new Story(m.group(1), m.group(2), m.group(3));
	}


	/**
	 *  Description of the Method
	 *
	 *@param  s  Description of the Parameter
	 */
	public static void grabStory(Story s) {
		StringBuffer  index  = new StringBuffer();
		if (mirrorDir.exists())
			Util.recursDel(mirrorDir);
		mirrorDir.mkdir();
		index.append("<html><head><title>Cachedot Mirror of \"" + s.getTitle() + "\"</title></head>\n");
		index.append("<body><h3>Cachedot Mirror of \"" + s.getTitle() + "\"</h3>\n");
		index.append("<ul>");
		for (Enumeration urls = s.getUrls(); urls.hasMoreElements(); ) {
			String  url      = (String) urls.nextElement();
			String  urlName  = s.getText(url);
			index.append("<li><a href=\"");
			index.append(url.substring(7, url.length()));
			index.append("\">" + urlName + "</a><br>");
			Util.wget(url, mirrorDir, 1);
		}
		index.append("</ul></body></html>");

		try {
			PrintWriter  pw  = new PrintWriter(new FileOutputStream(new File(mirrorDir, "index.html")));
			pw.print(index.toString());
			pw.close();
		}
		catch (Exception e) {
			Cachedot.log("Error writing index file: " + e);
		}
	}


	/**
	 *  Description of the Method
	 *
	 *@param  message  Description of the Parameter
	 */
	public static void log(String message) {
		System.err.println(message);
	}
}

/**
 *  A Slashdot story
 *
 *@author     Sanity
 *@created    July 20, 2002
 */
class Story {


	String     title, text, id;
	Hashtable  urls   = new Hashtable();


	/**
	 *  Constructor for the Story object
	 *
	 *@param  title  The title of the story
	 *@param  text   The text of the story
	 *@param  id     The Slashdot story ID
	 */
	public Story(String title, String text, String id) {
		// Remove HTML from title
		StringBuffer  titleSb  = new StringBuffer(title.length());
		boolean       k        = true;
		for (int x = 0; x < title.length(); x++) {
			char  c  = title.charAt(x);
			if (c == '<')
				k = false;
			else if (c == '>')
				k = true;
			else if (k)
				titleSb.append(c);
		}
		this.title = title.toString();

		// Remove '/'s from ID
		StringBuffer  idSb     = new StringBuffer(id.length());
		for (int x = 0; x < id.length(); x++) {
			char  c  = id.charAt(x);
			if (c != '/')
				idSb.append(c);
		}
		this.id = idSb.toString();

		Cachedot.log("Parsing story: '" + this.title + "' with id " + this.id);
		Pattern       urlex    = Pattern.compile("<A.*?HREF=\"(http://.*?)\".*?>(.*?)<", Pattern.CASE_INSENSITIVE);
		Matcher       m        = urlex.matcher(text);
		while (m.find()) {
			Cachedot.log("Found URL: " + m.group(1));
			urls.put(m.group(1), m.group(2));
		}
		Cachedot.log("Done parsing story");
	}


	/**
	 *  Gets the id attribute of the Story object
	 *
	 *@return    The id value
	 */
	public String getId() {
		return this.id;
	}


	/**
	 *  Gets the title attribute of the Story object
	 *
	 *@return    The title value
	 */
	public String getTitle() {
		return this.title;
	}


	/**
	 *  Gets an Enumeration of the URLs in this story
	 *
	 *@return    The urls value
	 */
	public Enumeration getUrls() {
		return urls.keys();
	}


	/**
	 *  Gets the text associated with a URL
	 *
	 *@param  url  Description of the Parameter
	 *@return      The text value
	 */
	public String getText(String url) {
		return (String) urls.get(url);
	}
}

/**
 *  Description of the Class
 *
 *@author     Sanity
 *@created    July 21, 2002
 */
class Util {


	static  Runtime  rt;
	static {
		rt = Runtime.getRuntime();
	}


	/**
	 *  Description of the Method
	 *
	 *@param  url    Description of the Parameter
	 *@param  dest   Description of the Parameter
	 *@param  depth  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	public static boolean wget(String url, File dest, int depth) {
		Cachedot.log("Mirroring " + url + " to " + dest);
		try {
			Process  p  = rt.exec("wget --recursive --level=" + depth + " --convert-links " + url, new String[0], dest);
			return (p.waitFor() == 0);
		}
		catch (Exception e) {
			Cachedot.log("Wget of " + url + " failed due to " + e);
			return false;
		}
	}


	/**
	 *  Description of the Method
	 *
	 *@param  dir   Description of the Parameter
	 *@param  name  Description of the Parameter
	 *@return       Description of the Return Value
	 */
	public static boolean fcpInsert(File dir, String name) {
		try {
			Cachedot.log("Starting fcpInsert of files in " + dir);
			Process  p  = rt.exec("fcpputsite -d -l 5 '" + name + "' " + dir + " Jfwpce58XD6gk~uOz4zy2rzV65g PZeKc90WU-8vdQ~Oc451Fw2tpEM");
			return (p.waitFor() == 0);
		}
		catch (Exception e) {
			Cachedot.log("Insert of " + name + " failed due to" + e);
			return false;
		}
	}


	/**
	 *  Description of the Method
	 *
	 *@param  del  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public static boolean recursDel(File del) {
		try {
			Process  p  = rt.exec("rm -rf " + del);
			return (p.waitFor() == 0);
		}
		catch (Exception e) {
			Cachedot.log("Delete of " + del + " failed due to " + e);
			return false;
		}
	}
}

