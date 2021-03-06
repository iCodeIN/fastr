### R code from vignette source '/s/a/k/fastr/library/utils/Sweave/example-1.Rnw'

### chunk #1: example-1.Rnw:13-16
data(airquality, package="datasets")
library("stats")
kruskal.test(Ozone ~ Month, data = airquality)


### chunk #3: example-1.Rnw:27-29
if(FALSE) { # [FastR] BEGIN Test snippet disabled due to graphics package use
library("graphics")
boxplot(Ozone ~ Month, data = airquality)
} # [FastR] END Test snippet disabled due to graphics package use


