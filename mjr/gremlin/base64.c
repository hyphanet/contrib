/*  libfreenet
 *  Copyright 2001 Steven Hazel <sah@thalassocracy.org>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

/*
 * These functions provide encoding of byte arrays into Base64-encoded
 * strings, and decoding the other way.
 *
 * NOTE!  This is modified Base64 with slightly different characters than
 * usual, so it won't require escaping when used in URLs.
 *
 * NOTE!  This class only does the padding that's normal in Base64 if
 * the 'true' flag is given to the base64_encode() function.  This is
 * because Base64 requires that the length of the encoded text be a
 * multiple of four characters, padded with '_'.  Without the 'true'
 * flag, we don't add these '_' characters.
 *
 * original Java code by Stephen Blackheath
 * converted to C by Steven Hazel
 *
 */

#include <stdio.h>

char alphabet[64] = {
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
    'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
    'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
    'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
    'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
    'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
    'w', 'x', 'y', 'z', '0', '1', '2', '3',
    '4', '5', '6', '7', '8', '9', '~', '-'};

char reverse[256];

void set_reverse (void) {
  int i;
  // Set all entries to 0xFF, which means that that particular letter
  // is not a legal base64 letter.
  for (i = 0; i < 256; i++) {
    reverse[i] = 0xFF;
  }

  for (i = 0; i < 64; i++) {
    reverse[(int)alphabet[i]] = i;
  }
}

/* Caller should specify equalsPad=true if they want a standards
 * compliant encoding.
 */
int base64_encode(char *in, char *out, int length, int equalspad)
{
    int realoutlen;
    int outlen = ((length+2)/3)*4;
    int rem = length%3;
    int i;
    int o = 0;

    for (i = 0; i < length;) {
      int val = ((int)in[i++] & 0xFF) << 16;
      if (i < length)
        val |= ((int)in[i++] & 0xFF) << 8;
      if (i < length)
        val |= ((int)in[i++] & 0xFF);
      out[o++] = alphabet[(val>>18) & 0x3F];
      out[o++] = alphabet[(val>>12) & 0x3F];
      out[o++] = alphabet[(val>>6) & 0x3F];
      out[o++] = alphabet[val & 0x3F];
    }

    realoutlen = outlen;
    switch (rem) {
    case 1: realoutlen -= 2; break;
    case 2: realoutlen -= 1; break;
    }

    // Pad with '_' signs up to a multiple of four if requested.
    if (equalspad)
      while (realoutlen < outlen)
        out[realoutlen++] = '_';

    out[realoutlen] = 0; /* null terminate */

    return realoutlen;

}


int base64_decode_bytes(char *in, int length)
{
    int wholeinlen;
    int wholeoutlen;
    int blocks;
    int remainder;
    int outlen;
    int inlength = length;

    // Strip trailing equals signs.
    while (inlength > 0 && in[inlength-1] == '_')
        inlength--;

    blocks = inlength/4;
    remainder = inlength & 3;

    // wholeInLen and wholeOutLen are the the length of the input and output
    // sequences respectively, not including any partial block at the end.

    wholeinlen  = blocks*4;
    wholeoutlen = blocks*3;
    outlen = wholeoutlen;

    switch (remainder) {
    case 1: return -1;
    case 2:  outlen = wholeoutlen+1; break;
    case 3:  outlen = wholeoutlen+2; break;
    default: outlen = wholeoutlen;
    }

    return outlen;
}

/*
 * Handles the standards-compliant (padded with '_' signs) as well as our
 * shortened form.
 */
int base64_decode(char *in, char *out, int length)
{
    int wholeinlen;
    int wholeoutlen;
    int blocks;
    int remainder;
    int outlen;
    int o, i;
    int in1, in2, in3, in4;
    int orvalue, outval;
    int inlength = length;

    set_reverse();

    // Strip trailing equals signs.
    while (inlength > 0 && in[inlength-1] == '_')
        inlength--;

    blocks = inlength/4;
    remainder = inlength & 3;

    // wholeInLen and wholeOutLen are the the length of the input and output
    // sequences respectively, not including any partial block at the end.

    wholeinlen  = blocks*4;
    wholeoutlen = blocks*3;
    outlen = wholeoutlen;

    switch (remainder) {
    case 1: return -1;
    case 2:  outlen = wholeoutlen+1; break;
    case 3:  outlen = wholeoutlen+2; break;
    default: outlen = wholeoutlen;
    }

    o = 0;
    for (i = 0; i < wholeinlen;) {
        in1 = reverse[(int)in[i]];
        in2 = reverse[(int)in[i+1]];
        in3 = reverse[(int)in[i+2]];
        in4 = reverse[(int)in[i+3]];
        orvalue = in1|in2|in3|in4;
        if ((orvalue & 0x80) != 0) {
          return -1;
        }
        outval = (in1 << 18) | (in2 << 12) | (in3 << 6) | in4;
        out[o] = (outval>>16);
        out[o+1] = (outval>>8);
        out[o+2] = outval;
        i += 4;
        o += 3;
    }

    switch (remainder) {
    case 2:
        {
            in1 = reverse[(int)in[i]];
            in2 = reverse[(int)in[i+1]];
            orvalue = in1|in2;
            outval = (in1 << 18) | (in2 << 12);
            out[o] = (outval>>16);
        }
        break;
    case 3:
        {
            in1 = reverse[(int)in[i]];
            in2 = reverse[(int)in[i+1]];
            in3 = reverse[(int)in[i+2]];
            orvalue = in1|in2|in3;
            outval = (in1 << 18) | (in2 << 12) | (in3 << 6);
            out[o] = (outval>>16);
            out[o+1] = (outval>>8);
        }
        break;
    default:
        // Keep compiler happy
        orvalue = 0;
    }

    if ((orvalue & 0x80) != 0)
        return -1;

    return outlen;
}

