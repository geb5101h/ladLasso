# ladLasso
LAD-Lasso estimator in Apache Spark

The LAD-Lasso is the solution to

![Latex of model](https://www.dropbox.com/s/wmhwcx72ydzwegz/ladLasso.gif?dl=1)

This software finds the solution using an ADMM algorithm (Boyd et al., 2011). 
For more details see the vignette [here](https://scholar.google.com/scholar?cluster=4526624859438373542&hl=en&as_sdt=0,14).

This implementation is for the data-distributed setting. It stores the dual variables in an RDD to obviate the need to run on a single machine.
