[![Join the chat at https://gitter.im/graalvm/graal-core](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/graalvm/graal-core?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

A high-performance implementation of the R programming language, built on GraalVM.

FastR aims to be:
* [efficient](https://medium.com/graalvm/faster-r-with-fastr-4b8db0e0dceb#4ab6): executing R language scripts faster than any other R runtime
* [polyglot](https://medium.com/graalvm/faster-r-with-fastr-4b8db0e0dceb#0f5c): allowing [polyglot interoperability](https://www.graalvm.org/docs/reference-manual/polyglot/) with other languages in the GraalVM ecosystem.
* [compatible](https://medium.com/graalvm/faster-r-with-fastr-4b8db0e0dceb#fff5): providing support for existing packages and the R native interface
* [embeddable](https://github.com/graalvm/examples/tree/master/r_java_embedding): allowing integration using the R embedding API or the GraalVM polyglot embedding SDK


The screenshot below shows Java application with embedded FastR engine.
The plot below was generated by `ggplot2` running on FastR and it shows
peak performance of the [raytracing example](http://www.tylermw.com/throwing-shade/).
The measurements were [reproduced independently](https://nextjournal.com/sdanisch/fastr-benchmark).

![Java embedding](documentation/assets/javaui.png)
![Speedup](documentation/assets/speedup.png)

 ## <a name="getting_started"></a>Getting Started
See the documentation on the GraalVM website on how to [get GraalVM](https://www.graalvm.org/docs/getting-started/) and [install and use FastR](http://www.graalvm.org/docs/reference-manual/languages/r/).

```
$ $GRAALVM/bin/R
Type 'q()' to quit R.
> print("Hello R!")
[1] "Hello R!"
>
```

## Documentation

The reference manual for FastR, which explains its advantages, its current limitations, compatibility and additional functionality is available on the [GraalVM website](http://www.graalvm.org/docs/reference-manual/languages/r/).

Further documentation, including contributor/developer-oriented information, is in the [documentation folder](documentation/Index.md) of this repository.

## Current Status

The goal of FastR is to be a drop-in replacement for GNU-R, the reference implementation of the R language.
FastR faithfully implements the R language, and any difference in behavior is considered to be a bug.

FastR is capable of running binary R packages built for GNU-R as long as those packages properly use the R extensions C API (for best results, it is recommended to install R packages from source).
FastR supports R graphics via the grid package and packages based on grid (like lattice and ggplot2).
We are currently working towards support for the base graphics package.
FastR currently supports many of the popular R packages, such as ggplot2, jsonlite, testthat, assertthat, knitr, Shiny, Rcpp, rJava, quantmod and more…

Moreover, support for dplyr and data.table are on the way.
We are actively monitoring and improving FastR support for the most popular packages published on CRAN including all the tidyverse packages.
However, one should take into account the experimental state of FastR, there can be packages that are not compatible yet, and if you try it on a complex R application, it can stumble on those.

## Stay connected with the community

See [graalvm.org/community](https://www.graalvm.org/community/) on how to stay connected with the development community.
The discussion on [gitter](https://gitter.im/graalvm/graal-core) is a good way to get in touch with us.

We would like to grow the FastR open-source community to provide a free R implementation atop the Truffle/Graal stack.
We encourage contributions, and invite interested developers to join in.
Prospective contributors need to sign the [Oracle Contributor Agreement (OCA)](http://www.oracle.com/technetwork/community/oca-486395.html).
The access point for contributions, issues and questions about FastR is the [GitHub repository](https://github.com/oracle/fastr).

## Authors

FastR is developed by Oracle Labs and is based on [the GNU-R runtime](http://www.r-project.org/).
It contains contributions by researchers at Purdue University ([purdue-fastr](https://github.com/allr/purdue-fastr)), Northeastern University, JKU Linz, TU Dortmund and TU Berlin.  

## License

FastR is available under a GPLv3 license.


