/* Copyright (C) 1999, 2000  Free Software Foundation

   This file is part of libgcj.

This software is copyrighted work licensed under the terms of the
Libgcj License.  Please consult the file "LIBGCJ_LICENSE" for
details.  */

#include <gcj/cni.h>
#include <stdint.h>
#include "MPN.h"

#ifdef USE_GMP
#include <gmp.h>
#endif

/** Add x[0:size-1] and y, and write the size least
 * significant words of the result to dest.
 * Return carry, either 0 or 1.
 * All values are unsigned.
 * This is basically the same as gmp's mpn_add_1.
 */

jint
MPN::add_1 (jintArray idest, jintArray ix, jint size, jint y)
{
  jint *dest = elements(idest);
  jint *x = elements(ix);
#ifdef USE_GMP
  return mpn_add_1 ((mp_limb_t *) dest, (mp_limb_t *) x, (mp_size_t) size, (mp_size_t) y);
#else
  jlong carry = (jlong)y & (jlong)0xffffffffL;
  for (jint i = 0;  i < size;  i++)
    {
      carry += ((jlong)x[i] & (jlong)0xffffffffL);
      dest[i] = (jint) carry;
      carry >>= 32;
    }
  return (jint) carry;
#endif
}

/** Add x[0:len-1] and y[0:len-1] and write the len least
 * significant words of the result to dest[0:len-1].
 * All words are treated as unsigned.
 * @return the carry, either 0 or 1
 * This function is basically the same as gmp's mpn_add_n.
 */

jint
MPN::add_n (jintArray idest, jintArray ix, jintArray iy, jint len)
{
  jint *dest = elements(idest);
  jint *x = elements(ix);
  jint *y = elements(iy);
#ifdef USE_GMP
  return mpn_add_n ((mp_limb_t *) dest, (mp_limb_t *) x, (mp_limb_t *) y, (mp_size_t) len);
#else  
  jlong carry = 0;
  for (jint i = 0; i < len;  i++)
    {
      carry += ((jlong) x[i] & (jlong)0xffffffffL) + ((jlong) y[i] & (jlong)0xffffffffL);
      dest[i] = (jint) carry;
      carry = (uint64_t) carry >> 32;
    }
  return (jint) carry;
#endif
}

/** Subtract Y[0:size-1] from X[0:size-1], and write
 * the size least significant words of the result to dest[0:size-1].
 * Return borrow, either 0 or 1.
 * This is basically the same as gmp's mpn_sub_n function.
 */

jint
MPN::sub_n (jintArray idest, jintArray iX, jintArray iY, jint size)
{
  jint *dest = elements(idest);
  jint *X = elements(iX);
  jint *Y = elements(iY);
#ifdef USE_GMP
  return mpn_sub_n ((mp_limb_t *) dest, (mp_limb_t *) X, (mp_limb_t *) Y, (mp_size_t) size);
#else
  jint cy = 0;
  for (jint i = 0;  i < size;  i++)
    {
      jint y = Y[i];
      jint x = X[i];
      y += cy;	/* add previous carry to subtrahend */
      // Invert the high-order bit, because: (uint32_t) X > (uint32_t) Y
      // iff: (jint) (X^0x80000000) > (jint) (Y^0x80000000).
      cy = (y^(jint)0x80000000) < (cy^(jint)0x80000000) ? 1 : 0;
      y = x - y;
      cy += (y^(jint)0x80000000) > (x ^ (jint)0x80000000) ? 1 : 0;
      dest[i] = y;
    }
  return cy;
#endif
}

/** Multiply x[0:len-1] by y, and write the len least
 * significant words of the product to dest[0:len-1].
 * Return the most significant word of the product.
 * All values are treated as if they were unsigned
 * (i.e. masked with 0xffffffffL).
 * OK if dest==x (not sure if this is guaranteed for mpn_mul_1).
 * This function is basically the same as gmp's mpn_mul_1.
 */

jint
MPN::mul_1 (jintArray idest, jintArray ix, jint len, jint y)
{
  jint *dest = elements(idest);
  jint *x = elements(ix);
#ifdef USE_GMP
  return mpn_mul_1 ((mp_limb_t *) dest, (mp_limb_t *) x, (mp_size_t) len, y);
#else 
  jlong yword = (jlong) y & (jlong)0xffffffffL;
  jlong carry = 0;
  for (jint j = 0;  j < len; j++)
    {
      carry += ((jlong) x[j] & (jlong)0xffffffffL) * yword;
      dest[j] = (jint) carry;
      carry = (uint64_t) carry >> 32;
    }
  return (jint) carry;
#endif
}

/**
 * Multiply x[0:xlen-1] and y[0:ylen-1], and
 * write the result to dest[0:xlen+ylen-1].
 * The destination has to have space for xlen+ylen words,
 * even if the result might be one limb smaller.
 * This function requires that xlen >= ylen.
 * The destination must be distinct from either input operands.
 * All operands are unsigned.
 * This function is basically the same gmp's mpn_mul. */

void
MPN::mul (jintArray idest, jintArray ix, jint xlen, jintArray iy, jint ylen)
{
  jint *dest = elements(idest);
  jint *x = elements(ix);
  jint *y = elements(iy); 
#ifdef USE_GMP
  mpn_mul ((mp_limb_t *) dest, (mp_limb_t *) x, (mp_size_t) xlen, (mp_limb_t *) y, (mp_size_t) ylen);
#else
  dest[xlen] = mul_1 (idest, ix, xlen, y[0]);

  for (jint i = 1;  i < ylen; i++)
    {
      jlong yword = (jlong) y[i] & (jlong)0xffffffffL;
      jlong carry = 0;
      for (jint j = 0;  j < xlen; j++)
        {
          carry += ((jlong) x[j] & (jlong)0xffffffffL) * yword
            + ((jlong) dest[i+j] & (jlong)0xffffffffL);
          dest[i+j] = (jint) carry;
          carry = (uint64_t) carry >> 32;
        }
      dest[i+xlen] = (jint) carry;
    }
#endif
}

/* Divide (unsigned jlong) N by (unsigned int) D.
 * Returns (remainder << 32)+(unsigned int)(quotient).
 * Assumes (unsigned int)(N>>32) < (unsigned int)D.
 * Code transcribed from gmp-2.0's mpn_udiv_w_sdiv function.
 */

jlong
MPN::udiv_qrnnd (jlong N, jint D)
{
  jlong q, r;
  jlong a1 = (uint64_t) N >> 32;
  jlong a0 = N & (jlong)0xffffffffL;
  if (D >= 0)
    {
      if ((uint64_t) a1 < ((D - a1 - ((uint64_t) a0 >> 31)) & (jlong)0xffffffffL))
        {
          /* dividend, divisor, and quotient are nonnegative */
          q = N / D;
          r = N % D;
        }
      else
        {
          /* Compute c1*2^32 + c0 = a1*2^32 + a0 - 2^31*d */
          jlong c = N - ((uint64_t) D << 31);
          /* Divide (c1*2^32 + c0) by d */
          q = c / D;
          r = c % D;
          /* Add 2^31 to quotient */
          q += 1 << 31;
        }
    }
  else
    {
      jlong b1 = (uint64_t) D >> 1;	/* d/2, between 2^30 and 2^31 - 1 */
      //jlong c1 = (a1 >> 1); /* A/2 */
      //jint c0 = (a1 << 31) + (a0 >> 1);
      jlong c = (uint64_t) N >> 1;
      if (a1 < b1 || (a1 >> 1) < b1)
        {
          if (a1 < b1)
            {
      	      q = c / b1;
      	      r = c % b1;
            }
          else /* c1 < b1, so 2^31 <= (A/2)/b1 < 2^32 */
            {
      	      c = ~(c - (b1 << 32));
      	      q = c / b1;  /* (A/2) / (d/2) */
      	      r = c % b1;
      	      q = (~q) & (jlong)0xffffffffL;    /* (A/2)/b1 */
      	      r = (b1 - 1) - r; /* r < b1 => new r >= 0 */
            }
          r = 2 * r + (a0 & 1);
          if ((D & 1) != 0)
            {
      	      if (r >= q)
                {
                  r = r - q;
            	}
              else if (q - r <= (D & (jlong)0xffffffffL))
                {
                  r = r - q + D;
            	  q -= 1;
            	}
              else
                {
                  r = r - q + D + D;
                  q -= 2;
                }
            }
        }
      else				/* Implies c1 = b1 */
        {				/* Hence a1 = d - 1 = 2*b1 - 1 */
          if (a0 >= ((jlong)(-D) & (jlong)0xffffffffL))
            {
      	      q = -1;
              r = a0 + D;
 	    }
          else
            {
      	      q = -2;
              r = a0 + D + D;
            }
        }
    }
  return (r << 32) | (q & (jlong)0xFFFFFFFFl);
}

/** Divide divident[0:len-1] by (unsigned int)divisor.
 * Write result into quotient[0:len-1.
 * Return the one-word (uint32_t) remainder.
 * OK for quotient==dividend.
 */

jint
MPN::divmod_1 (jintArray iquotient, jintArray idividend, jint len, jint divisor)
{
  jint *quotient = elements(iquotient);
  jint *dividend = elements(idividend);
#ifdef USE_GMP
  return mpn_divmod_1((mp_limb_t *) quotient, (mp_limb_t *) dividend, (mp_size_t) len, (mp_limb_t) divisor);
#else
  jint i = len - 1;
  jlong r = dividend[i];
  if ((r & (jlong)0xffffffffL) >= ((jlong) divisor & (jlong)0xffffffffL))
    r = 0;
  else
    {
      quotient[i--] = 0;
      r <<= 32;
    }
  for (; i >= 0; i--)
    {
      jint n0 = dividend[i];
      r = (r & ~(jlong)0xffffffffL) | ((jlong) n0 & (jlong)0xffffffffL);
      r = udiv_qrnnd (r, divisor);
      quotient[i] = (jint) r;
    }
  return (jint) (r >> 32);
#endif
}

/* Subtract x[0:len-1]*y from dest[offset:offset+len-1].
 * All values are treated as if unsigned.
 * @return the most significant word of
 * the product, minus borrow-out from the subtraction.
 */

jint
MPN::submul_1 (jintArray idest, jint offset, jintArray ix, jint len, jint y)
{
  jint *dest = elements(idest);
  jint *x = elements(ix);

  jlong yl = (jlong) y & (jlong)0xffffffffL;
  jint carry = 0;
  jint j = 0;
  do
    {
      jlong prod = ((jlong) x[j] & (jlong)0xffffffffL) * yl;
      jint prod_low = (jint) prod;
      jint prod_high = (jint) (prod >> 32);
      prod_low += carry;
      // Invert the high-order bit, because: (uint32_t) X > (uint32_t) Y
      // iff: (jint) (X^0x80000000) > (jint) (Y^0x80000000).
      carry = ((prod_low ^ (jint)0x80000000) < (carry ^ (jint)0x80000000) ? 1 : 0)
        + prod_high;
      jint x_j = dest[offset+j];
      prod_low = x_j - prod_low;
      if ((prod_low ^ (jint)0x80000000) > (x_j ^ (jint)0x80000000))
        carry++;
      dest[offset+j] = prod_low;
    }
  while (++j < len);
  return carry;
}

/** Divide zds[0:nx] by y[0:ny-1].
 * The remainder ends up in zds[0:ny-1].
 * The quotient ends up in zds[ny:nx].
 * Assumes:  nx>ny.
 * (int)y[ny-1] < 0  (i.e. most significant bit set)
 */

void
MPN::divide (jintArray izds, jint nx, jintArray iy, jint ny)
{
  jint *zds = elements(izds);
  jint *y = elements(iy);
#ifdef USE_GMP
  zds[nx] = mpn_divrem ((mp_limb_t *) &zds[ny], (mp_size_t) 0, (mp_limb_t *) zds, (mp_size_t) nx, (mp_limb_t *) y, (mp_size_t) ny);
#else
  // This is basically Knuth's formulation of the classical algorithm,
  // but translated from in scm_divbigbig in Jaffar's SCM implementation.

  // Correspondance with Knuth's notation:
  // Knuth's u[0:m+n] == zds[nx:0].
  // Knuth's v[1:n] == y[ny-1:0]
  // Knuth's n == ny.
  // Knuth's m == nx-ny.
  // Our nx == Knuth's m+n.

  // Could be re-implemented using gmp's mpn_divrem:
  // zds[nx] = mpn_divrem (&zds[ny], 0, zds, nx, y, ny).

  jint j = nx;
  do
    {                          // loop over digits of quotient
      // Knuth's j == our nx-j.
      // Knuth's u[j:j+n] == our zds[j:j-ny].
      jint qhat;  // treated as unsigned
      if (zds[j]==y[ny-1])
        qhat = -1;  // 0xffffffff
      else
        {
          jlong w = (((jlong)(zds[j])) << 32) + ((jlong)zds[j-1] & (jlong)0xffffffffL);
          qhat = (jint) udiv_qrnnd (w, y[ny-1]);
        }
      if (qhat != 0)
        {
          jint borrow = submul_1 (izds, j - ny, iy, ny, qhat);
          jint save = zds[j];
          jlong num = ((jlong)save&(jlong)0xffffffffL) - ((jlong)borrow&(jlong)0xffffffffL);
          while (num != 0)
            {
      	      qhat--;
      	      jlong carry = 0;
      	      for (jint i = 0;  i < ny; i++)
      	        {
      	          carry += ((jlong) zds[j-ny+i] & (jlong)0xffffffffL)
      	            + ((jlong) y[i] & (jlong)0xffffffffL);
      	          zds[j-ny+i] = (jint) carry;
      	          carry = (uint64_t) carry >> 32;
      	        }
      	      zds[j] += carry;
      	      num = carry - 1;
            }
        }
      zds[j] = qhat;
    } while (--j >= ny);
#endif
}

/** Number of digits in the conversion base that always fits in a word.
 * For example, for base 10 this is 9, since 10**9 is the
 * largest number that fits into a words (assuming 32-bit words).
 * This is the same as gmp's __mp_bases[radix].chars_per_limb.
 * @param radix the base
 * @return number of digits */

jint
MPN::chars_per_word (jint radix)
{
  if (radix < 10)
    {
      if (radix < 8)
        {
          if (radix <= 2)
            return 32;
          else if (radix == 3)
            return 20;
          else if (radix == 4)
            return 16;
          else
            return 18 - radix;
        }
      else
        return 10;
    }
  else if (radix < 12)
    return 9;
  else if (radix <= 16)
    return 8;
  else if (radix <= 23)
    return 7;
  else if (radix <= 40)
    return 6;
  // The following are conservative, but we don't care.
  else if (radix <= 256)
    return 4;
  else
    return 1;
}

/** Count the number of leading zero bits in an jint. */
jint
MPN::count_leading_zeros (jint i)
{
  if (i == 0)
    return 32;
  jint count = 0;
  for (jint k = 16;  k > 0;  k = k >> 1)
    {
      jint j = (uint32_t) i >> (uint32_t) k;
      if (j == 0)
        count += k;
      else
        i = j;
    }
  return count;
}

jint
MPN::set_str (jintArray idest, jbyteArray istr, jint str_len, jint base)
{
  jint *dest = elements(idest);
  jbyte *str = elements(istr);
#ifdef USE_GMP
  return mpn_set_str((mp_limb_t *) dest, (unsigned char *) str, (mp_size_t) str_len, base);
#else
  jint size = 0;
  if ((base & (base - 1)) == 0)
    {
      // The base is a power of 2.  Read the input string from
      // least to most significant character/digit.

      jint next_bitpos = 0;
      jint bits_per_indigit = 0;
      for (jint i = base; (i >>= 1) != 0; ) bits_per_indigit++;
      jint res_digit = 0;

      for (jint i = str_len;  --i >= 0; )
        {
          jint inp_digit = str[i];
          res_digit |= inp_digit << next_bitpos;
          next_bitpos += bits_per_indigit;
          if (next_bitpos >= 32)
            {
      	      dest[size++] = res_digit;
      	      next_bitpos -= 32;
      	      res_digit = inp_digit >> (bits_per_indigit - next_bitpos);
            }
        }

      if (res_digit != 0)
        dest[size++] = res_digit;
    }
  else
    {
      // General case.  The base is not a power of 2.
      jint indigits_per_limb = chars_per_word (base);
      jint str_pos = 0;

      while (str_pos < str_len)
        {
          jint chunk = str_len - str_pos;
          if (chunk > indigits_per_limb)
            chunk = indigits_per_limb;
          jint res_digit = str[str_pos++];
          jint big_base = base;

          while (--chunk > 0)
            {
      	      res_digit = res_digit * base + str[str_pos++];
      	      big_base *= base;
            }

          jint cy_limb;
          if (size == 0)
            cy_limb = res_digit;
          else
            {
      	      cy_limb = mul_1 (idest, idest, size, big_base);
      	      cy_limb += add_1 (idest, idest, size, res_digit);
            }
          if (cy_limb != 0)
            dest[size++] = cy_limb;
        }
     }
  return size;
#endif
}

/** Compare x[0:size-1] with y[0:size-1], treating them as unsigned jintegers.
 * @result -1, 0, or 1 depending on if x<y, x==y, or x>y.
 * This is basically the same as gmp's mpn_cmp function.
 */

jint
MPN::cmp (jintArray ix, jintArray iy, jint size)
{
  jint *x = elements(ix);
  jint *y = elements(iy);
#ifdef USE_GMP
  return mpn_cmp((mp_limb_t *) x, (mp_limb_t *) y, (mp_size_t) size);
#else 
  while (--size >= 0)
    {
      jint x_word = x[size];
      jint y_word = y[size];
      if (x_word != y_word)
        {
          // Invert the high-order bit, because:
          // (uint32_t) X > (uint32_t) Y iff
          // (jint) (X^0x80000000) > (jint) (Y^0x80000000).
          return (x_word ^ (jint)0x80000000) > (y_word ^ (jint)0x80000000) ? 1 : -1;
        }
    }
  return 0;
#endif
}

/** Compare x[0:xlen-1] with y[0:ylen-1], treating them as unsigned jintegers.
 * @result -1, 0, or 1 depending on if x<y, x==y, or x>y.
 */

jint
MPN::cmp (jintArray x, jint xlen, jintArray y, jint ylen)
{
  return xlen > ylen ? 1 : xlen < ylen ? -1 : cmp (x, y, xlen);
}

/* Shift x[x_start:x_start+len-1] count bits to the "right"
 * (i.e. divide by 2**count).
 * Store the len least significant words of the result at dest.
 * OK if dest==x.
 * Assumes: 0 <= count < 32
 * Same as rshift, but handles count==0 (and has no return value).
 */

void
MPN::rshift0 (jintArray idest, jintArray ix, jint x_start, jint len, jint count)
{
  jint *dest = elements(idest);
  jint *x = elements(ix);
  
  if (count > 0)
    rshift(idest, ix, x_start, len, count);
  else
    for (jint i = 0;  i < len;  i++)
      dest[i] = x[i + x_start];
}

/* Shift x[x_start:x_start+len-1]count bits to the "right"
 * (i.e. divide by 2**count).
 * Store the len least significant words of the result at dest.
 * The bits shifted out to the right are returned.
 * OK if dest==x.
 * Assumes: 0 < count < 32
 */

jint
MPN::rshift (jintArray idest, jintArray ix, jint x_start, jint len, jint count)
{
  jint *dest = elements(idest);
  jint *x = elements(ix);
#ifdef USE_GMP
  jint retval = x[x_start] << (32 - count);
  mpn_rshift((mp_limb_t *) dest, (mp_limb_t *) x + x_start, (mp_size_t) len, count);
  return retval;
#else
  jint count_2 = 32 - count;
  jint low_word = x[x_start];
  jint retval = low_word << count_2;
  jint i = 1;
  for (; i < len;  i++)
    {
      jint high_word = x[x_start+i];
      dest[i-1] = ((uint32_t) low_word >> (uint32_t) count) | (high_word << count_2);
      low_word = high_word;
    }
  dest[i-1] = (uint32_t) low_word >> (uint32_t) count;
  return retval;
#endif
}

/** Return the jlong-truncated value of right shifting.
* @param x a two's-complement "bignum"
* @param len the number of significant words in x
* @param count the shift count
* @return (jlong)(x[0..len-1] >> count).
*/

jlong
MPN::rshift_long (jintArray ix, jint len, jint count)
{
  jint *x = elements(ix);

  jint wordno = count >> 5;
  count &= 31;
  jint sign = x[len-1] < 0 ? -1 : 0;
  jint w0 = wordno >= len ? sign : x[wordno];
  wordno++;
  jint w1 = wordno >= len ? sign : x[wordno];
  if (count != 0)
    {
      wordno++;
      jint w2 = wordno >= len ? sign : x[wordno];
      w0 = ((uint32_t) w0 >> (uint32_t) count) | (w1 << (32-count));
      w1 = ((uint32_t) w1 >> (uint32_t) count) | (w2 << (32-count));
    }
  return ((jlong) w1 << 32) | ((jlong) w0 & (jlong)0xffffffffL);
}

/* Shift x[0:len-1] left by count bits, and store the len least
 * significant words of the result in dest[d_offset:d_offset+len-1].
 * Return the bits shifted out from the most significant digit.
 * Assumes 0 < count < 32.
 * OK if dest==x.
 */

jint
MPN::lshift (jintArray idest, jint d_offset, jintArray ix, jint len, jint count)
{
  jint *dest = elements(idest);
  jint *x = elements(ix);
#ifdef USE_GMP
  jint retval = (uint32_t) x[len-1] >> (uint32_t) (32 - count);
  mpn_lshift((mp_limb_t *) dest + d_offset, (mp_limb_t *) x, (mp_size_t) len, count);
  return retval;
#else  
  jint count_2 = 32 - count;
  jint i = len - 1;
  jint high_word = x[i];
  jint retval = (uint32_t) high_word >> (uint32_t) count_2;
  d_offset++;
  while (--i >= 0)
    {
      jint low_word = x[i];
      dest[d_offset+i] = (high_word << count) | ((uint32_t) low_word >> (uint32_t) count_2);
      high_word = low_word;
    }
  dest[d_offset+i] = high_word << count;
  return retval;
#endif
}

/** Return least i such that word&(1<<i). Assumes word!=0. */

jint
MPN::findLowestBit (jint word)
{
  jint i = 0;
  while ((word & (jint)0xF) == 0)
    {
      word >>= 4;
      i += 4;
    }
  if ((word & 3) == 0)
    {
      word >>= 2;
      i += 2;
    }
  if ((word & 1) == 0)
    i += 1;
  return i;
}

/** Return least i such that words & (1<<i). Assumes there is such an i. */

jint
MPN::findLowestBit (jintArray iwords)
{
  jint *words = elements(iwords);
  
  for (jint i = 0;  ; i++)
    {
      if (words[i] != 0)
        return 32 * i + findLowestBit (words[i]);
    }
}

/** Calculate Greatest Common Divisior of x[0:len-1] and y[0:len-1].
  * Assumes both arguments are non-zero.
  * Leaves result in x, and returns len of result.
  * Also destroys y (actually sets it to a copy of the result). */
#if 0
jint
MPN::gcd (jintArray ix, jintArray iy, jint len)
{
  jint *x = elements(ix);
  jint *y = elements(iy);
#ifdef USE_GMP_NO_WAY
  mp_limb_t mpl = mpn_gcd_1((mp_limb_t *) x, (mp_size_t) len, (mp_limb_t) *y);
  x = (jint *) &mpl;
  return ix->length;
#else
  jint i, word;
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
  jint initShiftWords = i;
  jint initShiftBits = findLowestBit (word);
  // Logically: sh = initShiftWords * 32 + initShiftBits

  // Temporarily devide both x and y by 2**sh.
  len -= initShiftWords;
  rshift0 (ix, ix, initShiftWords, len, initShiftBits);
  rshift0 (iy, iy, initShiftWords, len, initShiftBits);

  jintArray iodd_arg; /* One of x or y which is odd. */
  jintArray iother_arg; /* The other one can be even or odd. */
  
  if ((x[0] & 1) != 0)
    {
      iodd_arg = ix;
      iother_arg = iy;
    }
  else
    {
      iodd_arg = iy;
      iother_arg = ix;
    }

  jint *odd_arg = elements(iodd_arg);
  jint *other_arg = elements(iother_arg);
  
  for (;;)
    {
      // Shift other_arg until it is odd; this doesn't
      // affect the gcd, since we divide by 2**k, which does not
      // divide odd_arg.
      for (i = 0; other_arg[i] == 0; ) i++;
      if (i > 0)
        {
          jint j;
          for (j = 0; j < len-i; j++)
      	  other_arg[j] = other_arg[j+i];
          for ( ; j < len; j++)
            other_arg[j] = 0;
        }
      i = findLowestBit(other_arg[0]);
      if (i > 0)
        rshift (iother_arg, iother_arg, 0, len, i);

      // Now both odd_arg and other_arg are odd.

      // Subtract the smaller from the larger.
      // This does not change the result, since gcd(a-b,b)==gcd(a,b).
      i = cmp(iodd_arg, iother_arg, len);
      if (i == 0)
          break;
      if (i > 0)
        { // odd_arg > other_arg
          sub_n (iodd_arg, iodd_arg, iother_arg, len);
          // Now odd_arg is even, so swap with other_arg;
          jintArray tmp = iodd_arg; iodd_arg = iother_arg; iother_arg = tmp;
        }
      else
        { // other_arg > odd_arg
          sub_n (iother_arg, iother_arg, iodd_arg, len);
      }
      while (odd_arg[len-1] == 0 && other_arg[len-1] == 0)
        len--;
  }
  if (initShiftWords + initShiftBits > 0)
    {
      if (initShiftBits > 0)
        {
          jint sh_out = lshift (ix, initShiftWords, ix, len, initShiftBits);
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
#endif
}
#endif
jint
MPN::intLength (jint i)
{
  return 32 - count_leading_zeros (i < 0 ? ~i : i);
}

/** Calcaulte the Common Lisp "integer-length" function.
 * Assumes input is canonicalized:  len==BigInteger.wordsNeeded(words,len) */

jint
MPN::intLength (jintArray iwords, jint len)
{
  jint *words = elements(iwords);
  
  len--;
  return intLength (words[len]) + 32 * len;
}

