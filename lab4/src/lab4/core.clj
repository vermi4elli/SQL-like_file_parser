(ns lab4.core
  (:gen-class)
  (:use [clojure.data.csv]
        [clojure.data.json]
        [clojure.string]))

(require '[clojure.data.csv :as csv]
         '[clojure.java.io :as io]
         '[clojure.data.json :as js])

; ========================================
; The list of file names:

; MANDATORY
; "../data files/mp-posts_full.csv"
; "../data files/map_zal-skl9.csv"
; "../data files/plenary_register_mps-skl9.tsv"

; ADDITIONAL
; "../data files/plenary_vote_results-skl9.tsv"

; EXTRA
; "../data files/mps-declarations_rada.json"

; ========================================
; Just reading functions for files

;function for .csv parsing
(defn readCSV [path]
  (vec (vec (with-open [reader (io/reader path)]
              (doall
                (csv/read-csv reader))))))

;function for .tsv parsing
(defn readTSV [path]
  (apply conj (vector (clojure.string/split
                        (clojure.string/replace
                          (first
                            (vec
                              (doall
                                (line-seq
                                  (io/reader path))))) #"\t" "|") #"\|"))
              (for [string (rest (vec
                                   (doall
                                     (line-seq
                                       (io/reader path)))))]
                (conj
                  (vec
                    (butlast (clojure.string/split
                               (clojure.string/replace string #"\t" "/") #"/")))
                  (clojure.string/split
                    (last
                      (clojure.string/split
                        (clojure.string/replace string #"\t" "/") #"/")) #"\|")))))

;function for .json parsing
(defn readJSON [path]
  (vec (for [line (with-open [reader (io/reader path)]
         (doall
           (js/read reader)))]
         (zipmap (for [word (keys line)]
                   (keyword word))
                 (vals line)))))

; Using zipmap to create vector of maps with header line as keys
; for data in the following lines
(defn mapData
  [head & lines]
  (vec (map #(zipmap (map keyword head) %1) lines)))

(defn getExtension
  [path]
  (cond
    (= (last path) "n") (subs path ())))

;general function for parsing .csv, .tsv, .json files
(defn loadFile [path]
  (cond
    (clojure.string/ends-with? path ".csv")  (apply mapData (readCSV path))
    (clojure.string/ends-with? path ".tsv")  (apply mapData (readTSV path))
    (clojure.string/ends-with? path ".json") (readJSON path)
    :else "Incorrect file path or type!"))

; ========================================
; The parsed files and their choice:

; MANDATORY
(def mp-posts_full
  (loadFile "../data files/mp-posts_full.csv"))
(def map_zal-skl9
  (loadFile "../data files/map_zal-skl9.csv"))
(def plenary_register_mps-skl9
  (loadFile "../data files/plenary_register_mps-skl9.tsv"))

; ADDITIONAL
(def plenary_vote_results-skl9
  (loadFile "../data files/plenary_vote_results-skl9.tsv"))

; EXTRA
(def mps-declarations_rada
  (loadFile "../data files/mps-declarations_rada.json"))

; Returns the file you want to see
(defn choose_file
  [name]
  (case name
    "mp-posts_full" mp-posts_full
    "map_zal-skl9" map_zal-skl9
    "plenary_register_mps-skl9" plenary_register_mps-skl9
    "plenary_vote_results-skl9" plenary_vote_results-skl9
    "mps-declarations_rada" mps-declarations_rada
    ))

; ========================================
; Implementation for SELECT query

(def query
  ["plenary_register_mps-skl9" "date_agenda" "presence"])

(defn select
  [query]
  (let [[file & columns] query]
    (for [element (choose_file file)]
      (for [column (vec columns)] (get element (keyword column))))))

; ========================================
; Implementation for SELECT DISTINCT query

(def query
  ["mp-posts_full" "mp_id"])

(defn select_distinct
  [query]
  (vec (set (select query))))

; ========================================
; Implementation for WHERE query

; checks each line in
(defn check_expression
  [state data]
  (eval (read-string (clojure.string/join " " (vector
                                                "("
                                                (first state)
                                                data
                                                (nth state 1)
                                                ")")))))

; checks each line of the file on the conditions mentioned in clause
(defn check_true
  [clause data]
    (contains? (set (for [state clause]
        (when (= false
                 (check_expression
                   (vec (rest state))
                   (nth data (read-string (first state)))))
          -1))) -1))

; file is the result after 'select' query,
; clause has the [
;                 [ number_of_column, ">=" "<>"
(defn where
  [file clause]
  (remove nil? (vec
   (for [line file]
    (when (not (check_true clause line)) (vec line))))))

; ========================================
; Implementation for query parsing

(defn checkWhere
  [select_result commands clause]
  (println "==================CHECKWHERE==================")
  (println "commands")
  (println commands)
  (println "query")
  (println query)
  (println "clause")
  (println clause)
  (if (not= -1 (.indexOf commands "where"))
    (where select_result clause)
    select_result))

(defn checkSelect
  [query commands]
  (println "==================CHECKSELECT==================")
  (println "commands")
  (println commands)
  (println "query")
  (println query)
  (if (not= -1 (.indexOf commands "distinct"))
    (select_distinct query)
    (select query)))

; starts the execution of the correct function
(defn executeQuery
  [parsed_query]
  (println "==================EXECUTEQUERY==================")
  (let [commands (nth parsed_query 0)
        query (nth parsed_query 1)
        clause (if (nth parsed_query 2) (nth parsed_query 2)
                                        nil)]
    (println "commands")
    (println commands)
    (println "query")
    (println query)
    (println "clause")
    (println clause)
    (checkWhere (checkSelect query commands) commands clause)
))

; parses the clause (e.g.: from "mp_id>=21000" to [ "mp_id" ">=" "21000" ]
(defn getClause
  [clause columns]
  (println "==================GETCLAUSE==================")
  (if (nil? (clojure.string/index-of clause ">="))
    (vector (str (.indexOf columns (subvec clause 0 (clojure.string/index-of clause "<>"))))
            "<>"
            (subvec clause (+ 1 (clojure.string/index-of clause "<>"))))
    (vector (str (.indexOf columns (subvec clause 0 (clojure.string/index-of clause ">="))))
            ">="
            (subvec clause (+ 1 (clojure.string/index-of clause ">=")))))
  )

; gets those commands from the commands_list in the vector,
; which are present in the query
(defn getCommands
  [query commands_list]
  (filterv (fn [x] (if (not= -1 (.indexOf commands_list (clojure.string/lower-case x)))
                     true
                     false))
           query))

; gets the vector of all 'columns' from the file we're parsing
(defn getColumnsFromStar
  [file]
  (println "==================GETCOLUMNSFROMSTAR==================")
  (println "file")
  (println file)
  (loop [x 0
         result []]
    (if (< x (count (keys (first mp-posts_full))))
      (recur (+ x 1)
             (conj result (name (nth (keys (first mp-posts_full)) x))))
      result)))

; parses the query in the format:
; [ ["command1 (e.g. select)" "command2 (e.g. from)" ...]
;   ["file_name" "column1" "column2" ...]
; ]
(defn parseQuery
  [query_raw commands_list]
    (println "==================PARSEQUERY==================")
    (def commands (getCommands query_raw commands_list))
    (println "commands")
    (println commands)
    (def file (if (= -1 (.indexOf commands "where"))
                (subvec query_raw (+ 1 (.indexOf query_raw "from")))
                (subvec query_raw (+ 1 (.indexOf query_raw "from")) (.indexOf query_raw "where"))))
    (println "file")
    (println file)
    (def columns (cond
                   (not= (clojure.string/lower-case (nth query_raw 1)) "distinct")
                    (if (= (clojure.string/lower-case (nth query_raw 1)) "*")
                      (getColumnsFromStar file)
                      (subvec query_raw 1 (.indexOf query_raw "from")))
                   (= (clojure.string/lower-case (nth query_raw 1)) "distinct")
                    (if (= (clojure.string/lower-case (nth query_raw 2)) "*")
                      (getColumnsFromStar file)
                      (subvec query_raw 2 (.indexOf query_raw "from"))))
                   )
    (println "columns")
    (println columns)
    (def clause (if (not= -1 (.indexOf commands "where"))
                 (getClause (subvec query_raw (.indexOf query_raw "where")) columns)
                 nil))
    (println "clause")
    (println clause)
    (def query (apply conj file columns))
    (println "query")
    (println query)
    (vector commands query clause))

(defn -main [& args]
  (println "Write your commands here!")
  (let [input (read-line)
        commands_list ["select" "from" "where" "distinct"]]
    (executeQuery (parseQuery (clojure.string/split (clojure.string/replace input #"[,;]" "") #" ") commands_list)))
)

; temp data for testing
(def query
  ["plenary_register_mps-skl9" "date_agenda" "presence" "id_event"])

; temp data for testing
(def clause
  [
   ; where '1' stands for an index of "presence" in query
   ["1" ">=" "370"]
   ])