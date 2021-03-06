package edu.cmu.ml.proppr.util.math;

import java.util.Arrays;

import edu.cmu.ml.proppr.util.Dictionary;

/**
 * Encodes dense vectors V of unknown size, with constant-time access
 * to the components V[k].  Unlike an array, the maximum dimension
 * doesn't need to be specified in advance - the underlying array will
 * be resized as needed.
 * 
 * @author wcohen
 */

public class LongDense
{
	/** A vector of floats. **/

	static public abstract class AbstractFloatVector {
		/** Get the k-th component of the vector.
		 * I.e., v.get(k) conceptually returns V[k]
		 */
		abstract public float get(int k); 
		abstract public String toString();
		abstract public boolean definesIndex(int i);
	}

	static public class UnitVector extends AbstractFloatVector {
		/** A unit vector has value 1.0 at every component.
		 * todo: should probably be called Ones, not UnitVector
		 */
		public float get(int k) { return 1.0f; } 
		public String toString() { return "1.0..."; }
		public boolean definesIndex(int i) { return true; }
	}

	/** A float vector in which arbitrary floats can be stored.
	 */
	static public class FloatVector extends AbstractFloatVector {
		public float[] val;
		int maxIndex = -1;  // largest index actually used
		float dflt; // when asked for a value out of bounds

		public FloatVector(float[] v, int mi, float dflt) {
			this.val=v;
			this.maxIndex=mi;//this.val.length-1;
			this.dflt=dflt;
		}
		public FloatVector(float[] v, int mi) {
			this(v,mi,0.0f);
		}
		public FloatVector(int sizeHint) {
			this(new float[sizeHint],-1);
		}
		public FloatVector() {
			this(10);
		}

		/** The size of the smallest float[] array
		 * that could store this information.
		 */
		public int size() 
		{ 
			return maxIndex+1;
		}
		/** Set all components to zero.
		 */
		public void clear() 
		{
			for (int k=0; k<=maxIndex; k++) {
				val[k] = 0;
			}
		}
		public boolean definesIndex(int i) {
			return i<=maxIndex;
		}
		/** Return V[k] **/
		public float get(int k) 
		{
			growIfNeededTo(k);
			return val[k];
		}
		/** Increment V[k] by delta **/
		public void inc(int k, double delta) 
		{
			growIfNeededTo(k);
			val[k] += delta;
			maxIndex = Math.max(maxIndex,k);
		}
		/** Set V[k] to v  **/
		public void set(int k, double v) 
		{
			growIfNeededTo(k);
			val[k] = (float)v;
			maxIndex = Math.max(maxIndex,k);
		}
		private void growIfNeededTo(int k) 
		{
			// resize the underlying array if needed
			if (val.length <= k) {
				int n = Math.max(2*val.length, k+1);
				float[] tmp = new float[n];
				System.arraycopy(val,0, tmp,0, maxIndex+1);
				Arrays.fill(tmp, maxIndex+1, n, this.dflt);
				val = tmp;
			}
		}
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i=0;i<=maxIndex;i++) { sb.append("\n").append(val[i]); }
			return sb.substring(1);
		}
	}

	/** A vector of arbitrary objects. **/
	static public class ObjVector<T> {
		public Object[] val = new Object[10];
		int maxIndex = 0;

		/** The size of the smallest array that could store this
		 * information.
		 */

		public int size() 
		{ 
			return maxIndex+1;
		}
		/** Return V[k] **/
		public T get(int k) 
		{
			growIfNeededTo(k);
			return (T)val[k];
		}
		/** Store newval in V[k] **/
		public void set(int k, T newval) 
		{
			growIfNeededTo(k);
			val[k] = newval;
			maxIndex = Math.max(maxIndex,k);
		}
		/** Resize the underlying storage as needed **/
		private void growIfNeededTo(int k) 
		{
			if (val.length <= k) {
				int n = Math.max(2*val.length, k+1);
				Object[] tmp = new Object[n];
				System.arraycopy(val,0, tmp,0, maxIndex+1);
				val = tmp;
			}
		}
	}

	public static void main(String[] argv) throws Exception {
		LongDense.FloatVector v = new LongDense.FloatVector();
		for (int i=0; i<argv.length; i+=2) {
			int k = Integer.parseInt(argv[i]);
			double d = Double.parseDouble(argv[i+1]);
			v.inc(k,d);
		}
		for (int i=0; i<v.size(); i++) {
			System.out.println(i + ":\t" + v.get(i));
		}
	}
}
