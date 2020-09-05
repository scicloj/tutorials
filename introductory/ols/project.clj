(defproject ols "0.1.0-SNAPSHOT"
  :description "Simple OLS tutorial"
  :url ""
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 ;[org.clojars.haifengl/smile "2.5.1"] Java interop below seems more complete at least re documentation
                 [com.github.haifengl/smile-core "2.5.1"]
                 [com.github.haifengl/smile-mkl "2.5.1"]    ;necessary unless MKL is in your path
                 [techascent/tech.ml.dataset "4.01"]
                 [scicloj/tablecloth "1.0.0-pre-alpha9"]
                 ]
  :repl-options {:init-ns ols.core})
