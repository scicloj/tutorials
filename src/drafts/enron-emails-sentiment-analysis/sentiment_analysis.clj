;; ---
;; jupyter:
;;   jupytext:
;;     formats: ipynb,clj
;;     text_representation:
;;       extension: .clj
;;       format_name: light
;;       format_version: '1.4'
;;       jupytext_version: 1.1.1
;;   kernelspec:
;;     display_name: Clojure (clojupyter-v0.2.2)
;;     language: clojure
;;     name: clojupyter
;; ---

;; # Setup Environment

;; Enable stack traces
;; (clojupyter.misc.stacktrace/set-print-stacktraces! true)
(require '[clojupyter.misc.helper :as helper])
(helper/add-dependencies '[clojure-opennlp "0.5.0"])
(helper/add-dependencies '[kixi/stats "0.5.0"])
(helper/add-dependencies '[io.forward/clojure-mail "1.0.7"])
(helper/add-dependencies '[clojure2d "1.1.0"])
(helper/add-dependencies '[metasoarous/oz "1.5.0"])
(helper/add-dependencies '[clj-time "0.15.0"])
(print (str "Done!"))

;; Load VADER as local repository
;; The vader repo binary must be installed in this directory ./maven-repository
(do
    (use '[cemerick.pomegranate :only (add-dependencies)])
    (add-dependencies 
        :coordinates '[[local/vader "2.0.1"]] 
        :repositories {"local/vader" (str (.toURI (java.io.File. "./maven_repository")))}))

;; +
;; Build namespace
(ns drafts.sentiment_analysis
    (:import [net.nunoachenriques.vader SentimentAnalysis]
             [net.nunoachenriques.vader.lexicon English]
             [net.nunoachenriques.vader.text TokenizerEnglish]
             [java.io FileInputStream File]
             [javax.mail Session]
             [javax.mail.internet MimeMessage]
             [java.util Properties])
    (:require [kixi.stats.core :as stats]
              [clojure-mail.core :as mail]
              [clojure-mail.message :refer (read-message)]
              [oz.notebook.clojupyter :as oz]
              [clj-time.core :as t]
              [clj-time.coerce :as c]
              )
    (:use [clojure.repl :only (doc source)]
          [clojure.pprint :only (pprint)]
          [opennlp.nlp :only (make-sentence-detector)]))

*ns*
;; -

;; # Analyzing Sentiment w/ Vader

(def language (English.))
(def tokenizer (TokenizerEnglish.))

(def sa (SentimentAnalysis. language tokenizer))

(. sa (getSentimentAnalysis "Yay!! You are the best!"))

;; # Reading Emails

(def maildir-path "data/enron_mail/maildir")

;; +
(def sample-msg 
    (-> "data/enron_mail/maildir/arnold-j/_sent_mail/36."
        (clojure.java.io/as-file)
        (mail/file->message)
        (read-message)))

(pprint sample-msg)
;; -

;; # Read in Files

(defn get-files [start-path re]
    (->> start-path
         (clojure.java.io/as-file)
         (file-seq)
         (map #(.getPath %))
         (filter #(re-matches re %))))

(def xform-msg-files
    (comp (map mail/file->message)
          (map read-message)))

(def sent-mail-re #"data\/enron_mail\/maildir\/.*\/_sent_mail\/.*")
(def sent-msg-paths (get-files maildir-path sent-mail-re))

(defn msg-reduce
    ([] [])
    ([acc] acc)
    ([acc m]
        (conj acc {:to    (-> (get m :to) (first) (get :address))
                   :from  (-> (get m :from) (first) (get :address))
                   :date-sent (get m :date-sent)
                   :date-received (get m :date-received)
                   :subject (get m :subject)
                   :body  (get-in m [:body :body])})))

(def msgs (transduce xform-msg-files msg-reduce sent-msg-paths))

(count msgs)

;; # Add Message Sentiment

(defn remove-line-breaks [text]
    (clojure.string/replace text #"\n" ""))

(def get-sentences (make-sentence-detector "./models/en-sent.bin"))

(defn add-sentiment
    ([] [])
    ([acc] acc)
    ([acc msg]
      (conj acc (conj msg {:avg-sentiment (->> msg
                                     (:body)
                                     (get-sentences)
                                     (map remove-line-breaks)
                                     (map #(. sa (getSentimentAnalysis %)))
                                     (map #(get % "compound"))
                                     (transduce identity stats/mean))}))))

(def sentiment (transduce identity add-sentiment (filter #(< (count (get % :body)) 4000) msgs)))

;; # Plot Sentiment Over Time

(pprint (->> (take 10 sentiment)
             (map #(select-keys % [:date-sent :avg-sentiment]))))

(defn same-day? [t1 t2]
    (t/equal? (t/floor t1 t/day) (t/floor t2 t/day)))

(def xform-get-time-data
    (comp (map #(select-keys % [:date-sent :avg-sentiment]))
          (map #(hash-map :date (-> (c/from-date (:date-sent %))
                                    (t/floor t/day)
                                    (c/to-date))
                          :avg-sentiment (:avg-sentiment %)))))

(pprint (eduction xform-get-time-data (take 5 sentiment)))

(defn reduce-daily-sentiment
    ([] {})
    ([acc] 
     (reduce #(conj %1 {(first %2) 
                        (transduce identity stats/mean (second %2))}) (sorted-map) acc))
    ([acc x]
     (let [{date :date sentiment :avg-sentiment} x]
            (if (contains? acc date)
             (update acc date conj sentiment)
             (conj acc {date [sentiment]})))))

(def average-sentiment-data (transduce xform-get-time-data reduce-daily-sentiment sentiment))

(count average-sentiment-data)

;; +
(defn average [coll]
  (/ (reduce + coll)
      (count coll)))

(defn moving-average [period coll] 
  (lazy-cat (repeat (dec period) nil) 
            (map average (partition period 1  coll))))
;; -

(def time-series-data
    (->> average-sentiment-data
         (#(vector (map first %) (map second %)))
         (#(vector (first %) (second %) (moving-average 30 (second %))))
         (apply map vector)
         (map #(hash-map :date (str (nth % 0))
                         :avg-sentiment (nth % 1)
                         :moving-avg (nth % 2)))))

;; +
;; (def line-plot
;;   {:data {:values time-series-data}
;;    :width 400
;;    :height 400
;;    :encoding {:x {:field "date", :type "temporal"}
;;               :y {:field "moving-avg"}}
;;    :mark {:type "line" :stroke "red"}})

(def layered-line-plot
    {:width 600
     :height 600
     :data {:values time-series-data}
     :layer [{:mark {:type "line", :stroke "lightblue"}
              :encoding {:x {:field "date", :type "temporal"}
                         :y {:field "avg-sentiment"}}},
             {:mark {:type "line", :stroke "green"}
              :encoding {:x {:field "date", :type "temporal"}
                         :y {:field "moving-avg"}}}]})

;; Render the plot
;; (oz/view! line-plot)
(oz/view! layered-line-plot)
;; -


