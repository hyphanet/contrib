/*
  This code is distributed under the GNU Public Licence (GPL)
  version 2.  See http://www.gnu.org/ for further details of the GPL.
*/

/**
 * @author giannij
 **/
public class OnionFECEncoder extends NativeOnionFECEncoder {
    // Subclass explictly turns off native code so you
    // don't get a nasty looking linkage error on stderr.
    static {
        FECUtils.disableNativeCode();
    }
}




