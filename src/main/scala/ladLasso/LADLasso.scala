package org.apache.spark.mllib.regression

import org.apache.spark.mllib.linalg.{ Vector, Vectors, Matrix }
import breeze.linalg.{ DenseVector => DBV, DenseMatrix => DBM, diag, max, min, sum, norm, eigSym, Vector => BV, Matrix => BM }
import breeze.math._
import breeze.numerics._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.stat.MultivariateStatisticalSummary
import org.apache.spark.mllib.linalg.distributed.RowMatrix

/*
 * Implements the LADLasso, which is the solution to
 * min{b} (1/n*lambda) * ||y-bX||_1 + ||b||_1
 * 
 * We solve using the ADMM algorithm
 */
class LADLassoADMM(
    private var convergenceTol: Double,
    private var maxIterations: Int,
    private var regParam: Double,
    private var rho: Double) extends Serializable {

  def this(convergenceTol: Double, maxIterations: Int, regParam: Double) = this(convergenceTol, maxIterations, regParam, 1.0)

  def this() = this(1.0e-5, 100, 1e-3, 1.0)

  def setRegParam(rp: Double) = { regParam = rp }

  def setConvergenceTol(ct: Double) = { convergenceTol = ct }

  def setMaxIterations(mi: Int) = { maxIterations = mi }

  def run(data: RDD[(Double, Vector)]): LADLassoModel = {

    /* 
     * Currently only supports DenseVector
     * TODO: SparseVector support for large d
     */

    val mat = new RowMatrix(data
      .map(x => Vectors.dense(Array(1.0) ++ x._2.toArray)))

    val n = data.count
    val d = data.first._2.size+1

    val gram = mat.computeGramianMatrix().toBreeze.toDenseMatrix
    val gamma = eigSym(gram).eigenvalues(d - 1)

    var tol = Inf
    var iter = 1

    var betaNew, betaOld = BV[Double](Array.fill(d)(0.0))

    var iterRDD = data.map(x => LAD(x._1, BV(Array(1.0)++x._2.toArray), 0.0, 0.0, 0.0, 0.0))

    while (iter <= maxIterations && tol >= convergenceTol) {

      betaOld = betaNew

      var maxDiff = 0.0

      //update alpha
      iterRDD = iterRDD.map(obs => obs.updateAlpha(1.0 / (rho * n * regParam)))

      maxDiff = max(maxDiff, iterRDD.map(x => abs(x.alpha - x.alphaOld) / n).reduce(_ + _))

      //update beta
      val reduceB = iterRDD.map(obs => {
        obs.vector * (obs.alpha - obs.label - obs.u) / gamma
      }).reduce(_ + _)

      betaNew = LADLasso.softThreshold(betaOld - gram * betaOld / gamma - reduceB, 1.0 / (gamma * rho))

      maxDiff = max(maxDiff, sum(abs(betaNew.toDenseVector - betaOld.toDenseVector)))

      //update u
      //TODO: broadcast beta
      iterRDD.map(obs => obs.updateU(betaNew))

      maxDiff = max(maxDiff, iterRDD.map(x => abs(x.u - x.uOld) / n).reduce(_ + _))

      tol = maxDiff
      iter += 1

    }

    println("Finished after " + iter + " iterations\nwith tolerance " + tol)

    //extract intercept from weights vector
    val betaReturn = Vectors.fromBreeze(betaNew(1 to d - 1).toDenseVector)
    val intReturn = betaNew(0)

    new LADLassoModel(
      betaReturn, intReturn)
  }

}

/*
 * Some utility methods useful
 * in solving the LADLasso
 */
object LADLasso {

  def softThreshold(x: BV[Double], lambda: Double): BV[Double] = {
    val len = x.size
    val xNew = x.copy
    for (i <- 0 to len - 1) {
      xNew(i) = signum(xNew(i)) * max(0.0, math.abs(xNew(i)) - lambda)
    }
    xNew
  }

  def softThreshold(x: Double, lambda: Double): Double = {
    signum(x) * max(0.0, math.abs(x) - lambda)
  }
}

case class LAD(
    label: Double,
    vector: BV[Double],
    alpha: Double,
    u: Double,
    alphaOld: Double,
    uOld: Double) {

  def updateAlpha(thresh: Double) = this.copy(
    alpha = LADLasso.softThreshold(this.u - this.label, thresh),
    alphaOld = this.alpha)

  def updateU(beta: BV[Double]) = this.copy(
    u = this.u + this.label - this.alpha - this.vector.dot(beta),
    uOld = this.u)

}

class LADLassoModel(
  weights: Vector,
  intercept: Double) extends GeneralizedLinearModel(weights, intercept)
    with RegressionModel
    with Serializable {

  protected def predictPoint(
    dataMatrix: Vector,
    weightMatrix: Vector,
    intercept: Double): Double = {
    weightMatrix.toBreeze.dot(dataMatrix.toBreeze) + intercept
  }

}
