package dc;

// As of commit 75367fd, tutorialj ceased working

import static dc.TestUtilities.checkValidMarkovChain;
import static dc.TestUtilities.computeExactLogZ;
import static dc.TestUtilities.perfectBinaryTree;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import prototype.Node;
import tutorialj.Tutorial;
import xlinear.DenseMatrix;
import xlinear.Matrix;
import xlinear.MatrixOperations;
import bayonet.graphs.DirectedTree;
import bayonet.smc.ResamplingScheme;

public class Doc
{
  /**
   * 
   * Summary
   * -------
   * 
   * Two implementations of Divide and Conquer Sequential Monte Carlo (DC SMC), described in our 
   * [arXiv preprint.](http://arxiv.org/abs/1406.4993)
   * 
   * 1. One implementation has a specific model hard coded in it, namely the hierarchical model 
   *    with a binomial emission model used in section 5.2 of the above pre-print.
   * 2. A second implementation offers convenient interfaces to apply the algorithm to other models. 
   *    This second implementation also offers distributed and parallel computing functionalities.
   *    This second implementation is used in section 5.3 of the above pre-print.
   * 
   * Both implementations are based on Algorithm 2 of our arXiv preprint and at the moment do not support the 
   * extensions described in Section 4 of the preprint.
   *  
   * Since the paper explaining this method is under review, please contact us if you would like 
   * to use this software.
   * 
   * 
   * Installation
   * ------------
   * 
   * There are three ways to install:
   * 
   * ### Integrate to a gradle script
   * 
   * Simply add the following lines (replacing 1.0.0 by the current version (see git tags)):
   * 
   * ```groovy
   * repositories {
   *  mavenCentral()
   *  jcenter()
   *  maven {
   *     url "http://www.stat.ubc.ca/~bouchard/maven/"
   *   }
   * }
   * 
   * dependencies {
   *   compile group: 'ca.ubc.stat', name: 'divide-and-conquer-smc', version: '3.0.0'
   * }
   * ```
   * 
   * ### Compile using the provided gradle script
   * 
   * - Check out the source ``git clone git@github.com:alexandrebouchard/divide-and-conquer-smc.git``
   * - Compile using ``gradle installApp``
   * - Add the jars in ``build/install/divide-and-conquer-smc/lib/`` into your classpath
   * 
   * ### Use in eclipse
   * 
   * - Check out the source ``git clone git@github.com:alexandrebouchard/divide-and-conquer-smc.git``
   * - Type ``gradle eclipse`` from the root of the repository
   * - From eclipse:
   *   - ``Import`` in ``File`` menu
   *   - ``Import existing projects into workspace``
   *   - Select the root
   *   - Deselect ``Copy projects into workspace`` to avoid having duplicates
   *   
   *   
   * Running DC SMC on the binary emission hierarchical model (implementation 1)
   * ---------------------------------------------------------------------------
   * 
   * - Assuming you have checked out the repo and used ``gradle installApp`` successfully
   * - Download and prepare the data by typing ``scripts/prepare-data.sh`` from the root of the repository (requires wget). This writes the preprocessed data in ``data/preprocessedNYSData.csv``
   * - Run the software using ``scripts/run-simple.sh -inputData data/preprocessedNYSData.csv -nParticles 1000``. 
   * - Note: for the plots to work, you need to have R in your PATH variable (more precisely, Rscript) 
   * - Various output files are written in ``results/latest/``
   * 
   * The main interest of this implementation is for replicating the results in section 5.2 and 5.3 of 
   * our pre-print. To do so, see the following [separate public repository](https://github.com/alexandrebouchard/divide-and-conquer-smc-experiments) 
   * which contains the exact sets of options used to run the experiments as well as the plotting scripts.
   * To extend DC SMC to other models, use the second implementation instead, described next.
   * 
   * 
   * Running parallel and distributed DC SMC on the binary emission hierarchical model (implementation 2)
   * ----------------------------------------------------------------------------------------------------
   * 
   * - Assuming you have checked out the repo and used ``gradle installApp`` successfully
   * - Download and prepare the data by typing ``scripts/prepare-data.sh`` from the root of the repository (requires wget). This writes the preprocessed data in ``data/preprocessedNYSData.csv``
   * - Run the software using ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv``
   * 
   * ### Distributed and parallel computing options
   * 
   * To run in a distributed fashion, simply issue the same command line on different machines. The machines will 
   * discover each other (via multicast) and distribute the work dynamically. You can see in the output a variable 
   * ``nWorkers=[integer]`` showing the number of nodes cooperating. For testing purpose, you can do this on
   * one machine by opening two terminals (however, there is a better way to get multi-threading, see below).
   * 
   * The machines will only cooperate if all the command line options match exactly. You can check for example that running 
   * ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv -nParticles 2000`` will not cooperate with 
   * ``scripts/run-distributed.sh -dataFile data/preprocessedNYSData.csv -nParticles 1000``. You can also force groups 
   * of machines to avoid cooperation by using the ``-clusterSubGroup [integer]``
   * 
   * To use multiple threads within one machine, use ``-nThreadsPerNode [integer]``. This can be used in conjunction with 
   * a distributed computation, or without. 
   * 
   * 
   * ### Additional options
   * 
   * If you want the machines to wait each other, 
   * use ``-minimumNumberOfClusterMembersToStart [integer]``. This will cause machines to wait to start until this number 
   * of machines is gathered OR until ``maximumTimeToWaitInMinutes`` minutes has elapsed. 
   * Whether these options is used or not, machines can always join at any point in the execution of the algorithm. 
   * The waiting options were used for wall-clock 
   * running time analysis purpose. In most cases, these options can be ignored.
   * 
   * When the number of nodes in the DC-SMC tree is much larger than the number of threads times the number of nodes, 
   * it is useful to 
   * coarsen the granularity of the subtasks. To do so, you can use ``-maximumDistributionDepth [integer]``, which alters the 
   * way subtasks are created as follows: instead of the basic initial tasks being the leaves of the tree, they are the 
   * subtrees rooted at a depth from the root given by ``maximumDistributionDepth``. The subtrees under are computed 
   * serially within each node/thread. 
   * 
   * 
   * Using parallel and distributed DC SMC with your model
   * -----------------------------------------------------
   * 
   * First, build a class to encode each individual particle. The only requirement is that it should implement 
   * ``Serializable``. This will become generic type ``P`` in the following. 
   * 
   * Second, build a class to encode nodes in the tree. The only requirement is that you should override hashcode 
   * and equals to ensure that each node in the tree be unique (i.e. not .equals(..) with any other node, 
   * they will be inserted in a hashtable), AND reproducible (i.e. the default implementation of hashcode will 
   * depend on the memory location and will be different from machine to machine). A reasonable default implementation 
   * is in ``prototype.Node``. This will become generic type ``N`` in the following. 
   * 
   * Next, the main step consists in providing code that proposes, i.e. merges sub-populations. This is done by 
   * creating a class implementing ``dc.DCProposal``. This class will be responsible for both proposing, and 
   * providing a LOG weight update for the proposal (including taking care of computing the ratio in step 2(c) 
   * of Algorithm 2 in the arXiv pre-print). 
   * 
   * Here is an example, based on a simple model where transitions are provided 
   */
  @Tutorial(startTutorial = "README.md", showSource = true, showSignature = true)
  public static DCProposal<Integer> markovChainNaiveProposal(Matrix transition, Matrix prior) 
  {
    final int nStates = transition.nRows();
    checkValidMarkovChain(transition, prior);
    // Model: a finite state Markov chain, with..
    //   transition, a n by n transition matrix
    //   initial, a n by 1 initial distribution (i.e. prior on root)
    // NB: this is just for illustration/testing purpose as one can do exact inference 
    //     on this model
    return new DCProposal<Integer>() { // In this example, particles are just integers
      /**
       * Propose a parent particle given the children. 
       * All the randomness should be obtained via the provided random object.
       * 
       * @param random
       * @param childrenParticles
       * @return A pair containing the LOG weight update and the proposed particle
       *    In the basic algorithm, this is given by gamma(x') / q(x'|x_1, .., x_C) / gamma(x_1) / .. / gamma(x_C)
       *    Where x' is the tree, and x_1, .., x_C are the C children subtrees.
       */
      @Override
      public Pair<Double, Integer> propose(Random random, List<Integer> childrenParticles)
      {
        // Naive proposal: uniform
        Integer proposal = random.nextInt(nStates);
        double weightUpdate = nStates; // 1 / q(x'|x), where q(x'|x) = 1/nStates
        weightUpdate *= prior.get(proposal, 0); // prior(proposal) is part of gamma(x')
        for (Integer childState : childrenParticles)
        {
          weightUpdate *= transition.get(proposal, childState); // transition(child | proposal) is part of gamma(x')
          weightUpdate /= prior.get(proposal, 0); // everything else in gamma(x_c) gets cancelled with gamma(x')
        }
        return Pair.of(Math.log(weightUpdate), proposal);
      }
    };
  }
  
  /**
   * After building a DCProposal, we also need a DCProposalFactory, which instantiate one proposal for each 
   * node of the tree and each thread and node. 
   * 
   * This can be useful to produce different types of proposal depending on the part of the tree. For example, 
   * in our simple example where we arbitrarily set all the observations to be the state 0, the proposals at the 
   * leaves propose the value 0 deterministically, while the other nodes used the proposal defined above.
   */
  @Tutorial(startTutorial = "README.md", showSource = true, showSignature = true)
  public static DCProposalFactory<Integer,Node> markovChainNaiveProposalFactory(Matrix transition, Matrix prior) 
  {
    return new DCProposalFactory<Integer,Node>() 
    {
      @Override
      public DCProposal<Integer> build(
          Random random, 
          Node currentNode,
          List<Node> childrenNodes)
      {
        if (childrenNodes.size() == 0)
          // In this toy example, we set the leaves as observed and equal to 0
          // This is modelled as a proposal that always returns state 0 if this 
          // is a leaf.
          return (rand, children) -> Pair.of(Math.log(prior.get(0, 0)), 0); // NB: DCProposal is a FunctionalInterface
        else
          // Else, return the proposal defined above
          return markovChainNaiveProposal(transition, prior);
      }
    };
  }
  
  // simple example used just for pedagogical reasons
  public static DenseMatrix transition = MatrixOperations.denseCopy(new double [][] {
      {0.8, 0.2},
      {0.2, 0.8}
    });
    
  // NB: this does not need to be the stationary of the above
  public static DenseMatrix prior = MatrixOperations.denseCopy(new double[] {0.5, 0.5});
  
  /**
   * Here is an example of how to put it all together:
   */
  @Test
  @Tutorial(showSource = true, showLink = true, linkPrefix = "src/test/java")
  public void testMarkovChainExample()
  {
    System.out.println(transition);
    // 2 x 2 dense matrix
    //       0         1       
    // 0 |   0.800000  0.200000
    // 1 |   0.200000  0.800000
    
    System.out.println(prior);
    // 2 x 1 dense matrix
    // 0       
    // 0 |   0.500000
    // 1 |   0.500000
    
    DCProposalFactory<Integer,Node> factory = markovChainNaiveProposalFactory(transition, prior);
    
    // Topology, here a simple perfect binary tree of depth 3
    final DirectedTree<Node> tree = perfectBinaryTree(3);
    
    // Use DCOptions to set option, either programmatically as below, 
    // or see DDCMain for an example how to parse these options from command line.
    DCOptions options = new DCOptions();
    options.nThreadsPerNode = 4; 
    options.nParticles = 1_000_000;
    options.masterRandomSeed = 31; // Note: given the seed, output is deterministic even if distributed and/or parallelized
    options.resamplingScheme = ResamplingScheme.MULTINOMIAL; // currently supported: STRATIFIED and MULTINOMIAL (default)
    options.relativeEssThreshold = 0.5; // only resample if relative ESS fall below this threshold
    
    // Prepare the simulation
    DistributedDC<Integer, Node> ddc = DistributedDC.createInstance(options, factory, tree);
    
    // By default, the processor below is included, which prints and records in the result folder 
    // some basic statistics like ESS, logZ estimates, etc. I.e. the line below is not needed but 
    // examplifies how other custom processors would be added.
    //   instance.addProcessorFactory(new DefaultProcessorFactory<>());
    
    // Perform the  sampling
    ddc.start();
    // timing: node=0, ESS=548622.6672945316, rESS=0.5486226672945316, logZ=-3.5950250310183574, nWorkers=1, iterationProposalTime=196, globalTime=3324
    
    // Compare to exact log normalization obtained by sum product:
    final double exactLogZ = computeExactLogZ(transition, prior, tree, (node) -> 0);
    System.out.println("exact = " + exactLogZ);
    // exact = -3.600962588536195
    
    final double relativeError = Math.abs(exactLogZ - ddc.getRootPopulation().logNormEstimate())/exactLogZ;
    Assert.assertTrue(relativeError < 0.01);
  }
  
  // TODO: add some basic doc in the separate repo on experiments divide-and-conquer-smc-experiments
  // TODO: add gradle bootstrap script
}
