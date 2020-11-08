(ns cljfromr.core
  (:require [nrepl.server])
  (:gen-class
   :name cljfromr.core
   :methods [#^{:static true} [foo [double] double]
             #^{:static true} [startnrepl [int] java.lang.String]]))

(defn -startnrepl [port]
  (nrepl.server/start-server :port port)
  "ok")

(defn foo [x]
  (* 9 x))

(defn -foo [x]
  (foo x))
