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

;; # Time-Series Sentiment Analysis of Enron Email Dataset

;; ## Goals
;;
;; By the end of this tutorial you will know how to:
;; * Use the VADER sentiment analysis model
;; * Use transducers to transform data in compact form
;; * Load a local dependency 
;; * Chart data using the [oz](https://github.com/metasoarous/oz) visualization library (base don vega-lite).

;; ## Prepare the Notebook 

;; To get started, we need to prepare the clojure notebook context. Because we are using the clojupyter kernel, we have access to core clojure methods, but we will also be using a set of other dependencies. We load these dependencies using `clojupyter.misc.helper/add-dependences`.

;; +
;; Enable stack traces
;; (clojupyter.misc.stacktrace/set-print-stacktraces! true)
(require '[clojupyter.misc.helper :as helper])

(->> '[[clojure-opennlp "0.5.0"]
       [kixi/stats "0.5.0"]
       [io.forward/clojure-mail "1.0.7"]
       [clojure2d "1.1.0"]
       [metasoarous/oz "1.5.0"]
       [clj-time "0.15.0"]
       [net.cgrand/xforms "0.18.2"]]
     (map helper/add-dependencies)
     doall)

:done
;; -

;; The VADER repository that we are going to use ([nunoachenriques/vader-sentiment-analysis](https://github.com/nunoachenriques/vader-sentiment-analysis)) is as of this writing not available through clojure's repository hub (maven). Therefore, we need to load it locally. 
;;
;; To do this, the basic steps are as follows:
;; 1. Acquire the JAR package;
;; 2. Create a local directory off the root of the project called `maven-repository` and place the JAR package in that directory;
;; 3. Add the JAR in the local repository directory to mvn locally:
;;     ```bash 
;;    > mvn install:install-file -Dfile=./maven_repository/vader-sentiment-analysis-2.0.1.jar -Durl=file:repo -DgroupId=local -DartifactId=vader -Dversion=2.0.1 -Dpackaging=jar
;;     ```
;;
;; Once these steps are completed, we can then load the dependency using the `cemerick.pomegrnatae/add-depenceis` method, as follows. 

;; > Note that the package name referenced, below `local/vader`, corresponds with the `groupID` and `artifactId` in the `mvn` command we used to add the JAR to maven above.

;; Load VADER as local repository
;; The vader repo binary must be installed in the dir ./maven-repository as specified below
(do
    (use '[cemerick.pomegranate :only (add-dependencies)])
    (add-dependencies 
        :coordinates '[[local/vader "2.0.1"]] 
        :repositories {"local/vader" (str (.toURI (java.io.File. "./maven_repository")))}))

;; ## Declaring a namespace

;; All methods in clojure programs exist in a namespace. Each namespace also includes dependency declarations. The same is true when using Clojure in a notebook context.
;;
;; The default namespace for clojure in a notebook context is `user`. We can see that that is the case by inspecting the current namespace like so:

*ns*

;; Although we could simply operate in this user namespace, it's better practice to define our namespace specifically. [**Is it really??**] So let's do that:

;; +
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
              [clj-time.core :as t]
              [clj-time.coerce :as c]
              [net.cgrand.xforms :as x]
              [oz.notebook.clojupyter :as oz])
    (:use [clojure.repl :only (doc source)]
          [clojure.pprint :only (pprint print-table)]
          [opennlp.nlp :only (make-sentence-detector)]))

*ns*
;; -

(set! *warn-on-reflection* true)

;; ## Analyzing Sentiment w/ Vader

;; Now that we have a namespace declared we can begin our exploration using VADER sentiment analysis.
;;
;; What is VADER? Vader is a sentiment analysis tool optimized for analyzing informal language, in particular the type of language that appears in social media and other micro-blogging tools. If you'd like to learn more about this model, please refer to the paper ["VADER: A Parsimonious Rule-based Model for Sentiment Analysis of Social Media Text"](http://comp.social.gatech.edu/papers/icwsm14.vader.hutto.pdf) by C.J. Hutton & Eric Gilbert.
;;
;; The [VADER package we are using](https://github.com/nunoachenriques/vader-sentiment-analysis) here is an implementation of VADER in Java. In order to use it, we first define a language and a tokenizer to match and then instantiate a VADER sentiment analysis class.

(def language (English.))
(def tokenizer (TokenizerEnglish.))

(def ^SentimentAnalysis sa (SentimentAnalysis. language tokenizer))


;; > Note: We are defining the type of the `sa` variable as an instance of the `SentimentAnalysis` class. This is useful because... **[TODO: Fill in reason]**]
;;
;; Now that we have the VADER class, let' use it:

(. sa (getSentimentAnalysis "Yay!! You are the best!"))

;; As you can see, we get four numbers back. Each of these numbers is in the range `-1 > x < 1`. They express the degree of negative, netural, and positive sentiment independently, while the "compound" number gives a summary of all three measures rolled up into one.
;;
;; That's all fine and good. Now to get a sense of what makes VADER unique, look at what happens when we analyze the sentiment of `:)` and `:(`.

(. sa (getSentimentAnalysis ":)"))

(. sa (getSentimentAnalysis ":("))

;; ## Reading Emails
;;

(def maildir-path "data/enron_mail/maildir")


;; +
(def sample-msg 
    (-> "data/enron_mail/maildir/arnold-j/_sent_mail/36."
        (clojure.java.io/as-file)
        (mail/file->message)
        (read-message)))

(pprint sample-msg)
;; -

;; ## Read All Messages

;; Now that we know how to use VADER, let's build our data. The enron messages are stored in individual files, so we'll need to pull them in one a a time. 
;;
;; In clojure, we can do this in a very concise and compact manner by taking advantage of function composition and a more advanced tool called "transducers" -- which stands for transformer-reducer. If you'd like to read up on transducers before you proceed, go [here]() and [here]().

;; Getting started, the first thing we'll need is a way to grab all the files that we want from the dataset. Let's define a function that does this for us. This function takes a starting path and a regular expression (`re`). It pulls all the files that are children of that starting path and filters them using the regular expression. Nice! 

(defn get-files [start-path re]
    (->> start-path
         (clojure.java.io/as-file)
         (file-seq)
         (map #(.getPath ^File %))
         (filter #(re-matches re %))))

;; Now, defining a regex blob to match only sent mail files, we use this function to gather all the paths for the files that interest us.

(def sent-mail-re #"data\/enron_mail\/maildir\/.*\/_sent_mail\/.*")
(def sent-msg-paths (get-files maildir-path sent-mail-re))

;; Next, once we have the files, we need to convert them into message data. Again, we can write a simple function to do this:

(defn raw-message->message-data [m]
    {:to    (-> (get m :to) (first) (get :address))
     :from  (-> (get m :from) (first) (get :address))
     :date-sent (get m :date-sent)
     :date-received (get m :date-received)
     :subject (get m :subject)
     :body  (get-in m [:body :body])})

;; Now that we have some crucial functions defined, we can now combine them to do the transformation we need all at once. It's at this point that we leverage the power of clojure's utilities and concepts as a functional language.
;;
;; In clojure, we can declare the transformations that we want in the abstract before actually making them. This is different than when we use a language like Python because with Python we tend to make those transformations one-at-a-time, imperatively.
;;
;; So how does this wwrk? To declare these transformative proceses we use what are called "transforms" or, more fancily, "transducers." A transducer is just a function that can transform one reducing function to another. Most sequence functions clojure will return a transducer if the collection upon which the function would normally act is omitted. 
;;
;; So:

(pprint (map str [1 2 3])) ;; returns ["1" "2" "3"]
(pprint (map str))         ;; returns a transducer

;; Using `map` in this way along with the `comp` function, which composes a functions together, we can create a transformation that will read the message files using the functions we've declared and a few helper functions imported from the `clojure-mail` package:

(def xform-msg-files
    (comp (map mail/file->message)
          (map read-message)
          (map raw-message->message-data)))

;; As you can see, these transformation stacks are incredibly easy to read. What does our `xform-msg-files` do? It will eventually receive a collection and pass that collection through the series of mapping statements. So what we have here is a clearly defined pipeline transformation that does the following:
;;
;; 1. Load a MIME message from a file (via clojure-mail);
;; 2. Convert the MIME message to a data structure (via clojur-mail);
;; 3. Build our own more refined data structure (via raw-message->message-data).
;;
;; Now that we've defined our transformtion, let's use it! 
;;
;; To do so, we can take advatage of the fact that the `sequence` method has an arity that accepts a transducer as its first argument along with a collection as its second argument:

(def msgs (sequence xform-msg-files sent-msg-paths))

(count msgs)

(pprint (take 1 msgs))

;; ## Add Message Sentiment

;; We have our files into the `msg` variable, so now we can analyze the msg content with VADER. Our goal here is to take an intial look at the enron data by visualizing the average sentiment of all sent messages on a given day over time.
;;
;; To get this done we'll define another transform function. A couple of steps are required:
;;
;; 1. Break the messages into individual sentences;  
;; 2. Clean the msgs by removing line break symbols;  
;; 3. Calculate message sentiment by analyzing each sentence individually and then taking the average.
;; 4. Average the sentiment of all messages for a given day.
;;
;; We are breaking the message into sentences and analyzing each sentence individually because this is what the creator of the library suggest is the best method. The methods for cleaning and then parsing the sentences are relatively simple. To sentencize the messages we use a method from the `clojure-opennelp` project. 

(defn remove-line-breaks [text]
    (clojure.string/replace text #"\n" ""))

(def get-sentences (make-sentence-detector "./models/en-sent.bin"))


;; Now that we have our methods for cleaning and sentencizing, we put them all together in a function that takes a message and returns its average sentiment:

(defn msg->avg-sentiment [msg]
  (->> msg
       (:body)
       (get-sentences)
       (transduce
        (map (fn [sentence]
               (-> sentence
                   remove-line-breaks
                   (#(. ^SentimentAnalysis sa (getSentimentAnalysis %)))
                   (get "compound"))))
        stats/mean)))

;; You'll notice that this function also uses the concept of transducers, even thought we didn't formally define a transform. In this case, the transform function is defined in-line (see the `map` expression, and remember it returns a transducer if not provided a collection). 
;;
;; This transform is then provided an argument to the `transduce` function. `transduce` takes both a transform and a reducer, and then applies the reducer function to the result of the transform. 
;;
;; In this case, our `map`-generated transform converts a sentence into that sentence's compound sentiment score. The reducer function `stats/mean` then takes those compound sentiment scores and returns their average. See how concise and expressive clojure data science can be!?
;;
;; Now to put our `msg->avg-sentiment` function to work, we'll define and execute another transformation using `sequence`. We'll limit the size of the emails to 4000 characters in order to make the process faster:
;;

(def sentiment 
    (sequence
          (comp 
            (filter #(< (count (get % :body)) 4000))
            (map (fn [msg] (conj msg {:avg-sentiment (msg->avg-sentiment msg)}))))
          msgs))

(->> sentiment
     (take 10)
     (map #(select-keys % [:date-sent :avg-sentiment]))
     print-table)

;; ## Building Time-Series Data

;; At this point, we have in our `sentiment` variable a data structure containing an average compound score for all the sentences in each individual message and the time that message was sent.
;;
;; We are geting close to our goal. What we need to do now is get an overall sentiment score for all the messages on a given day. To do this, we will flatten all the time stamps in the `:date-sent` so that they only show the day that they were sent. 
;;
;; Then once we've done that we'll build an average score for each day. And just to get a bit fancier we will also calculate a 30-day moving average.
;;
;; We'll begin with a function that transforms a dates to a base time relative to UTC. The key to this is `clj-time`'s `floor` method.

(defn get-time-data [{:keys [date-sent avg-sentiment]}]
    {:date (-> date-sent
               c/from-date
               (t/floor t/day)
               (c/to-date))
     :avg-sentiment avg-sentiment})

(->> sentiment
     (eduction (comp (take 5)
                     (map get-time-data)))
     print-table)

;; Now that we've normalized the `:date`, we can do our final transform. Our goal is to gather together all the sentiment scores for each day and then take their average.  We can express exactly this idea in a concise manner using a new function, `by-key`, from the net.cgrand/xforms library.
;;
;; Let's look at the documentation for this function:

(doc x/by-key)

;; Essentially, `by-key` takes a key function (`kfn`) that it uses to partition the collection it is provided; then, it applies the transform (`xform`) that has been provided to each partition. `by-key` also has an arrity in which you provide a "value" function (`vfn`) to extract the "value part" from each piece of data. On top of all that, `by-key` returns a transducer, so we can use it in much the same way that we have been using the `map` function as input into any other Clojure function that accepts transducers.
;;
;; So how do we build the call to `by-key`. Well, remember that in Clojure keywords are also functions that if provided a map as an argument will extract the value associated with that key. Therefore we can simply use the `:date` keyword as our partition function, and `:avg-sentiment` as our value function. Finally, we provide a transform function provided by the same library to take the average: `x/avg`:

(def average-sentiment-data (into (sorted-map)
                                  (comp (map get-time-data)
                                        (x/by-key :date
                                                  :avg-sentiment
                                                  x/avg))
                                  sentiment))

(count average-sentiment-data)

;; As you can see, we've reduced our dataset size considerably. Now we have a single average sentiment score for all messages for each day. 
;;
;; Our next and final step before visualizing is to add a moving average. We won't narrate this transformation as it uses pretty basic Clojure functions and the code speaks for itself.

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
         (#(vector (keys %)
                   (vals %)
                   (moving-average 30 (vals %))))
         (apply map (fn [date v smoothed-v]
                        {:date (str date)
                         :avg-sentiment v
                         :moving-avg smoothed-v}))))

(print-table (take 40 time-series-data))

;; ## Visualization

;; We've finally arrived at the exciting moment of visualization. The tool we'll use to chart our data is called `oz`. It is built on top of a `vega-lite`, which is a highly declarative charting language built in JavaScript that provides an alternative to `d3`. 
;;
;; It's declartive syntax makes it pretty easy to understand. All we'll really do here is define a configuration map that specifies the visual characteristics of the plot we want along with our data. Then we call `oz/view!` on that configuration. Here it is!

;; +
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
