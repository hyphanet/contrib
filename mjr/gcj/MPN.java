/* Copyright (C) 1999, 2000  Free Software Foundation

   This file is part of libgcj.

This software is copyrighted work licensed under the terms of the
Libgcj License.  Please consult the file "LIBGCJ_LICENSE" for
details.  */

//package gnu.gcj.math.MPN;

public class MPN
{
  public static native int add_1 (int[] dest, int[] x, int size, int y);
  public static native int add_n (int dest[], int[] x, int[] y, int len);
  public static native int sub_n (int[] dest, int[] X, int[] Y, int size);
  public static native int mul_1 (int[] dest, int[] x, int len, int y);
  public static native void mul (int[] dest, int[] x, int xlen, int[] y, int ylen);
  public static native long udiv_qrnnd (long N, int D);
  public static native int divmod_1 (int[] quotient, int[] dividend, int len, int divisor);
  public static native int submul_1 (int[] dest, int offset, int[] x, int len, int y);
  public static native void divide (int[] zds, int nx, int[] y, int ny);
  public static native int chars_per_word (int radix);
  public static native int count_leading_zeros (int i);
  public static native int set_str (int[] dest, byte[] str, int str_len, int base);
  public static native int cmp (int[] x, int[] y, int size);
  public static native int cmp (int[] x, int xlen, int[] y, int ylen);
  public static native void rshift0 (int[] dest, int[] x, int x_start, int len, int count);
  public static native int rshift (int[] dest, int[] x, int x_start, int len, int count);
  public static native long rshift_long (int[] x, int len, int count);
  public static native int lshift (int[] dest, int d_offset, int[] x, int len, int count);
  public static native int findLowestBit (int word);
  public static native int findLowestBit (int[] words);
//  public static native int gcd (int[] x, int[] y, int len);
  public static native int intLength (int i);
  public static native int intLength (int[] words, int len);
  
  public static int gcd (int[] x, int[] y, int len)
  {
    int i, word;
    // Find sh such that both x and y are divisible by 2**sh.
    for (i = 0; ; i++)
      {
        word = x[i] | y[i];
        if (word != 0)
          {
            // Must terminate, since x and y are non-zero.
            break;
          }
      }
    int initShiftWords = i;
    int initShiftBits = findLowestBit (word);
    // Logically: sh = initShiftWords * 32 + initShiftBits

    // Temporarily devide both x and y by 2**sh.
    len -= initShiftWords;
    MPN.rshift0 (x, x, initShiftWords, len, initShiftBits);
    MPN.rshift0 (y, y, initShiftWords, len, initShiftBits);

    int[] odd_arg;
    int[] other_arg;
    if ((x[0] & 1) != 0)
      {
        odd_arg = x;
        other_arg = y;
      }
    else
      {
        odd_arg = y;
        other_arg = x;
      }

    for (;;)
      {
        // Shift other_arg until it is odd; this doesn't
        // affect the gcd, since we divide by 2**k, which does not
        // divide odd_arg.
        for (i = 0; other_arg[i] == 0; ) i++;
        if (i > 0)
          {
            int j;
            for (j = 0; j < len-i; j++)
                other_arg[j] = other_arg[j+i];
            for ( ; j < len; j++)
              other_arg[j] = 0;
          }
        i = findLowestBit(other_arg[0]);
        if (i > 0)
          MPN.rshift (other_arg, other_arg, 0, len, i);

        // Now both odd_arg and other_arg are odd.

        // Subtract the smaller from the larger.
        // This does not change the result, since gcd(a-b,b)==gcd(a,b).
        i = MPN.cmp(odd_arg, other_arg, len);
        if (i == 0)
            break;
        if (i > 0)
          { // odd_arg > other_arg
            MPN.sub_n (odd_arg, odd_arg, other_arg, len);
            // Now odd_arg is even, so swap with other_arg;
            int[] tmp = odd_arg; odd_arg = other_arg; other_arg = tmp;
          }
        else
          { // other_arg > odd_arg
            MPN.sub_n (other_arg, other_arg, odd_arg, len);
        }
        while (odd_arg[len-1] == 0 && other_arg[len-1] == 0)
          len--;
    }
    if (initShiftWords + initShiftBits > 0)
      {
        if (initShiftBits > 0)
          {
            int sh_out = MPN.lshift (x, initShiftWords, x, len, initShiftBits);
            if (sh_out != 0)
              x[(len++)+initShiftWords] = sh_out;
          }
        else
          {
            for (i = len; --i >= 0;)
              x[i+initShiftWords] = x[i];
          }
        for (i = initShiftWords;  --i >= 0; )
          x[i] = 0;
        len += initShiftWords;
      }
    return len;
  }

}

