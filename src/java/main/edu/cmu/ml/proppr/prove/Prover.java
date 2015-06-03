package edu.cmu.ml.proppr.prove;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.ml.proppr.prove.wam.Argument;
import edu.cmu.ml.proppr.prove.wam.LogicProgramException;
import edu.cmu.ml.proppr.prove.wam.Outlink;
import edu.cmu.ml.proppr.prove.wam.ProofGraph;
import edu.cmu.ml.proppr.prove.wam.Query;
import edu.cmu.ml.proppr.prove.wam.State;
import edu.cmu.ml.proppr.prove.wam.VariableArgument;
import edu.cmu.ml.proppr.util.APROptions;
import edu.cmu.ml.proppr.util.Dictionary;
import edu.cmu.ml.proppr.util.SymbolTable;

/**
 * abstract prover class - prove a goal, constructing a proof graph
 * @author "William Cohen <wcohen@cs.cmu.edu>"
 * @author "Kathryn Mazaitis <krivard@cs.cmu.edu>"
 *
 */
public abstract class Prover {
	private static final boolean NORMLX_RESTART = true;
	private static final boolean NORMLX_TRUELOOP = true;
	// necessary to avoid rounding errors when rescaling reset weight
	protected static final double ALPHA_BUFFER = 1e-16;
	protected FeatureDictWeighter weighter;
	protected APROptions apr;
	public Prover() {
		this(new APROptions());
	}
	public Prover(APROptions apr) {
		this(new UniformWeighter(), apr);
	}
	public Prover(FeatureDictWeighter w, APROptions apr) {
		this.weighter = w;
		this.apr = apr;
	}
	/** Return unfiltered distribution of state associated with proving the start state. 
	 * @throws LogicProgramException */
	public abstract Map<State,Double> prove(ProofGraph pg) throws LogicProgramException;
	
	/** Return a threadsafe copy of the prover */
	public abstract Prover copy();
	
	public void setWeighter(FeatureDictWeighter w) {
		this.weighter = w;
	}
	/**
	 * z = sum[edges] f( sum[features] theta_feature * weight_feature )
	 * rw = f( sum[resetfeatures] theta_feature * weight_feature )
	 *    = f( theta_alphaBooster * weight_alphaBooster + sum[otherfeatures] theta_feature * weight_feature )
	 * nonBoosterReset = sum[otherfeatures] theta_feature * weight_feature = f_inv( rw ) - theta_alphaBooster * weight_alphaBooster
	 * 
	 * assert rw_new / z_new = alpha
	 * z_new = z_old - rw_old + rw_new = z_old - rw_old + alpha * z_new
	 * (1 - alpha) * z_new = z_old - rw_old
	 * z_new = [1 / (1 - alpha)] * (z_old - rw_old)
	 * then:
	 * 
	 * f( theta_alphaBooster * newweight_alphaBooster + sum[otherfeatures] theta_feature * weight_feature ) = alpha * z_new
	 *  = [alpha / (1 - alpha)] * (z_old - rw_old)
	 * theta_alphaBooster * newweight_alphaBooster + f_inv( rw_old ) - theta_alphaBooster * oldweight_alphaBooster = f_inv( [alpha / (1 - alpha)] * (z_old - rw_old) )
	 * newweight_alphaBooster = (1/theta_alphaBooster) * (f_inv( [alpha / (1 - alpha)] * (z_old - rw_old)) - (f_inv( rw_old ) - theta_alphaBooster))
	 * 
	 * NB f_inv( [alpha / (1 - alpha)] * (z_old - rw_old) ) - nonBoosterReset < 0 when default reset weight is high relative to z;
	 * when this is true, no reset boosting is necessary and we can set newweight_alphaBooster = 0.
	 * @param currentAB
	 * @param z
	 * @param rw
	 * @return
	 */
	protected double computeAlphaBooster(double currentAB, double z, double rw) {
		double thetaAB = Dictionary.safeGet(this.weighter.weights,ProofGraph.ALPHABOOSTER,this.weighter.weightingScheme.defaultWeight());
		if (thetaAB == 0) return 0; // then nothing we can do to currentAB matters
		double nonBoosterReset = this.weighter.weightingScheme.inverseEdgeWeightFunction(rw) - thetaAB * currentAB;
		double alpha_fraction = (this.apr.alpha + ALPHA_BUFFER) / (1 - (this.apr.alpha + ALPHA_BUFFER));
		double numerator = (this.weighter.weightingScheme.inverseEdgeWeightFunction( alpha_fraction * (z - rw) ) - nonBoosterReset); 
		return Math.max(0,numerator / thetaAB);
	}
	protected double rescaleResetLink(Outlink reset, double z) {
			double newAB = computeAlphaBooster(reset.fd.get(ProofGraph.ALPHABOOSTER), z, reset.wt);
			z -= reset.wt;
			reset.fd.put(ProofGraph.ALPHABOOSTER,newAB);
			reset.wt = this.weighter.w(reset.fd);
			z += reset.wt;
			return z;
	}
	protected Map<State,Double> normalizedOutlinks(ProofGraph pg, State s) throws LogicProgramException {
		List<Outlink> outlinks = pg.pgOutlinks(s,NORMLX_TRUELOOP);
		Map<State,Double> weightedOutlinks = new HashMap<State,Double>();
		double z = 0;
		for (Outlink o : outlinks) {
			o.wt = this.weighter.w(o.fd);
			weightedOutlinks.put(o.child, o.wt);
			z += o.wt;
		}
		
		for (Map.Entry<State,Double>e : weightedOutlinks.entrySet()) {
			e.setValue(e.getValue()/z);
		}
		return weightedOutlinks;
	}
	public Map<Query,Double> solvedQueries(ProofGraph pg) throws LogicProgramException {
		Map<State,Double> ans = prove(pg);
		Map<Query,Double> solved = new HashMap<Query,Double>();
		for (Map.Entry<State,Double> e : ans.entrySet()) {
			if (e.getKey().isCompleted()) solved.put(pg.fill(e.getKey()),e.getValue());
		}
		return solved;
	}
	public Map<String,Double> solutions(ProofGraph pg) throws LogicProgramException {
		Map<State,Double> proveOutput = this.prove(pg);
		Map<String,Double> filtered = new HashMap<String,Double>();
		double normalizer = 0;
		for (Map.Entry<State, Double> e : proveOutput.entrySet()) {
			normalizer += e.getValue();
			if (e.getKey().isCompleted()) {
				Map<Argument,String> d = pg.asDict(e.getKey());
				String dstr = "";
				if (!d.isEmpty()) dstr = Dictionary.buildString(d,new StringBuilder()," ").substring(1);
				filtered.put(dstr, Dictionary.safeGet(filtered,dstr)+e.getValue());
			}
		}
		for (Map.Entry<String,Double> e : filtered.entrySet()) {
			e.setValue(e.getValue()/normalizer);
		}
		return filtered;
	}
}