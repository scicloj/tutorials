# cljfromr

A basic example of using Clojure from R

## Intro 

This small repo presents an almost-minimal example of calling Clojure from R, while developing both sides in an interactive fashion.

It uses [rJava](http://www.rforge.net/rJava/), a JNI-based bridge for calling R from Java.

We have been exploring more comprehensive solutions, with a decent support for data conversion, an easier notation and a more dynamic experience. All that may become a library in the future.

## Requirement
* R
* rJava
* Leiningen

## Usage

Start R at the main project directory, and do the following:

```{r}
# Load the rJava library.
library(rJava)

# Initialize the JVM.
.jinit()

# Build the Clojure project as a standalone JAR
# (unnecessary if you have already done so).
system("lein uberjar")

# Add the Clojure project jar to the classpath.
.jaddClassPath("target/cljfromr-0.1.0-SNAPSHOT-standalone.jar")

# Call the Clojure function `cljfromr.core/foo`.
J("cljfromr.core")$foo(10)

# Start an nREPL server at port 1111
# for interactive Clojure development.
J("cljfromr.core")$startnrepl(1111L)
```
Now you may connect your Clojure development environment to the nREPL session at port 1111, and edit the [Clojure code](src/cljfromr/core.clj) to interactively redefine the function `cljfromr.core/foo`.

Then, try it again from the same R session.

```{r}
J("cljfromr.core")$foo(10)
```

You should see the result updated to match the new definition.

## Known issues

* While connecting to nREPL (see the comments in the code), your development environment might be missing some runtime dependencies such as [cider-nrepl](https://github.com/clojure-emacs/cider-nrepl). To solve that, you may add them (with the relevant version) to [project.clj](./project.clj).
* Underscores and hyphens in names might be a bit tricky, due to the Clojure compilation rules. So, if you are trying this code with different names, you should expect some errors around that till you get it right.

## Related projects
* [rmarkdown-clojure](https://github.com/genmeblog/rmarkdown-clojure) - using Clojure in RMarkdown notebooks
* [rscala](https://github.com/dbdahl/rscala) - calling R from Scala from R (user a different approach, based on a multi-session TCP/IP based solution, and presents some nice ideas in its R API)
* [clojisr](https://github.com/scicloj/clojisr) - calling R from Clojure

## License

Copyright © 2020 Scicloj

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
