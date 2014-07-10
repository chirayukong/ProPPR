package edu.cmu.ml.praprolog.learn.tools;

import java.util.HashMap;
import java.util.Map;

import edu.cmu.ml.praprolog.util.Dictionary;

public class LossData {
	public Map<LOSS,Double> loss=new HashMap<LOSS,Double>();
	public enum LOSS {
		REGULARIZATION,
		LOG,
		L2
	}
	public void clear() {
		this.loss.clear();
	}
	public double total() {
		double total=0;
		for(Double d : loss.values()) total += d;
		return total;
	}
	/**
	 * Return a new LossData containing (this.loss.get(x) - that.loss.get(x)) for every loss type x in either object.
	 * @param that
	 * @return
	 */
	public LossData diff(LossData that) {
		LossData diff = new LossData();
		for (LOSS x : this.loss.keySet()) {
			diff.loss.put(x, this.loss.get(x) - Dictionary.safeGet(that.loss,x,0.0));
		}
		for (Map.Entry<LOSS, Double> x : that.loss.entrySet()) {
			if (!this.loss.containsKey(x.getKey()))
				diff.loss.put(x.getKey(), -x.getValue());
		}
		return diff;
	}
	/**
	 * Return a deep copy of this LossData.
	 * @return
	 */
	public LossData copy() {
		LossData copy = new LossData();
		copy.loss.putAll(this.loss);
		return copy;
	}
}
