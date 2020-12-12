(defproject ols "0.1.2-SNAPSHOT"
  :description "Simple OLS tutorial"
  :url ""
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.github.haifengl/smile-core "2.5.3"]
                 [com.github.haifengl/smile-mkl "2.5.3"]
                 ;[org.clojars.haifengl/smile "2.5.3"]; not necessary
                 ;[org.bytedeco/arpack-ng "3.7.0-1.5.3"];can't get this to work
                 [techascent/tech.ml.dataset "5.00-beta-18"]
                 [scicloj/notespace "3-alpha3-SNAPSHOT"]]
  :repl-options {:init-ns ols.core})
