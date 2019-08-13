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
              ;; [java-time :as dt]
              ;; [clojure2d.color :as c]
              ;; [cljplot.render :as r]
              ;; [cljplot.build :as b]
              ;; [cljplot.common :refer :all]
              [clojure-mail.message :refer (read-message)])
    (:use [clojure.repl :only (doc source)]
          [clojure.pprint :only (pprint)]
          [opennlp.nlp :only (make-sentence-detector)]))

*ns*

(def language (English.))
(def tokenizer (TokenizerEnglish.))

(def sa (SentimentAnalysis. language tokenizer))

(. sa (getSentimentAnalysis "Yay!! You are the best!"))

(def maildir-path "data/enron_mail/maildir")

(def sample-msg 
    (-> "data/enron_mail/maildir/arnold-j/_sent_mail/36."
        (clojure.java.io/as-file)
        (mail/file->message)
        (read-message)))

(pprint sample-msg)

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

(defn remove-line-breaks [text]
    (clojure.string/replace text #"\n" ""))

(def get-sentences (make-sentence-detector "./models/en-sent.bin"))

(defn date-and-sentiment
    ([] [])
    ([acc] acc)
    ([acc msg]
     (conj acc (conj {:date (get msg :date-sent)}
                     {:avg-sentiment (->> msg
                                     (:body)
                                     (get-sentences)
                                     (map remove-line-breaks)
                                     (map #(. sa (getSentimentAnalysis %)))
                                     (map #(get % "compound"))
                                     (transduce identity stats/mean))}))))

(def sentiment (transduce identity date-and-sentiment msgs))




