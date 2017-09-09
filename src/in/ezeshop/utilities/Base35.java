package in.ezeshop.utilities;

/**
 * Created by adgangwa on 08-04-2016.
 */
public class Base35 {
    // 0 to be used as filler
    // 'capital O' not used to avoid confusion with 0
    private static final String ALPHABET = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";

    private static final int BASE = ALPHABET.length();

    private Base35() {}

    public static String fromBase10(long i, int idLen) {
        StringBuilder sb = new StringBuilder("");
        while (i > 0) {
            i = fromBase10(i, sb);
        }
        //String base61 =  sb.reverse().toString();
        sb.reverse();

        // add 0 as filler
        for (int toAppend = (idLen-sb.length()); toAppend>0; toAppend--) {
            sb.append('0');
        }
        return sb.toString();
    }

    private static long fromBase10(long i, final StringBuilder sb) {
        int rem = (int)(i % BASE);
        sb.append(ALPHABET.charAt(rem));
        return i / BASE;
    }

    public static int toBase10(String str) {
        return toBase10(new StringBuilder(str).reverse().toString().toCharArray());
    }

    private static int toBase10(char[] chars) {
        int n = 0;
        for (int i = chars.length - 1; i >= 0; i--) {
            n += toBase10(ALPHABET.indexOf(chars[i]), i);
        }
        return n;
    }

    private static int toBase10(int n, int pow) {
        return n * (int) Math.pow(BASE, pow);
    }
}
