(ns unicorn.core
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.data.json :as json]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [response]]
            [org.httpkit.timer :refer :all]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.json])
  (:import [com.mongodb MongoOptions ServerAddress]))


;; Db Connection


(def ^MongoOptions opts (mg/mongo-options {:threads-allowed-to-block-for-connection-multipler 300}))
(def ^ServerAddress sa (mg/server-address "127.0.0.1" 27017))
(def conn (mg/connect sa opts))

(def db (mg/get-db conn "cinema-lambda"))

;; Functions to DB

(defn insert-payment-db [doc]
  (mc/insert-and-return db "payments" {:sl_id (:id doc) :sl_name (:name doc)}))

(defn insert-code-db [code]
  (mc/insert-and-return db "codes" {:code code :used false }))

(defn find-code-db [code]
  (mc/find-one-as-map db "codes" {:code code}))


;; Utils


(defn generate-code []
  (let [code (.toString (java.util.UUID/randomUUID))]
    (insert-code-db code)))

(defn secs [seconds] (* 1000 seconds))

(defn int-or-nothing [number] (if (pos-int? number) number nil))


;; General States


(def clients (atom {}))

;; TASKS could have types:
;;   * VIDEO
;;   * EVENT
;;   * ????

(def tasks [{:type "VIDEO" :time (+ (System/currentTimeMillis) (secs 10)) :data "JSON_DATA_VIDEO"}
            {:type "EVENT" :time (+ (System/currentTimeMillis) (secs 20)) :data "JSON_DATA_EVENT"}])


;; WebSockets


;; Read task for scheduler
(defn send-to-all [msg]
  (doseq [client (keys @clients)]
    (server/send! client msg)))

(defn on-receive-handler [data channel]
  (case (:command (json/read-str data :key-fn keyword))
    "check" (server/send! channel "Hola!")))

(schedule-task 10000 (send-to-all "change-video"))

(defn ws-handler [request]
  (server/with-channel request channel
    ;; (println channel)
    (swap! clients assoc channel true)
    (loop [id 0]
      (when (< id 10)
        (schedule-task (* id 2000)
                       (server/send! channel (str "message from the server #" id) false))
        (recur (inc id))))
    (server/on-close channel (fn [status] (println "channel closed: " status)))
    (server/on-receive channel #(on-receive-handler % channel))))

;; - Pass tasks to ____ to program scheduler

(defn scheduler-generator
  "It function receive a UNIX-timestamp and will calculate the rest time in ms
  to execute the task. (Well don't know if this is the correct approach)"
  [unix-time f data]
  (if-let [time (int-or-nothing (- unix-time (System/currentTimeMillis)))]
    (schedule-task time (f data))))

(defn prepare-tasks
  [tasks]
  (doseq [task tasks]
    (scheduler-generator (:time task) send-to-all (:data task))))


;; API
;; These endpoint can receive JSON


(defn save-configuration
  [req]
  (prepare-tasks tasks)
  {:status 200 :body (:body req)})

(defn pay
  [req]
  (insert-payment-db (:body req))
  (let [code (generate-code)]
    {:status 200 :body code}))

(defn verify-code
  [code]
  (if-let [code (find-code-db code)]
    {:status 200 :body {:access true}}
    {:status 404 :body {:access false :msg "Code no valid!"}}))


;; Router


(defroutes app-routes
  (GET "/" [] (str "Welcome to home!"))
  (GET "/ws" [] ws-handler)
  (ANY "/anything-goes" [] (str "Any method accepted"))
  (POST "/save-configuration" [] save-configuration)
  (POST "/pay" [] pay)
  (GET "/verify-code" [code] (verify-code code))
  (route/not-found "You must be new here"))

(defn -main
  "This is our app's entry point."
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "9090"))]
    (server/run-server (-> #'app-routes
                           wrap-params
                           (#(wrap-json-body % {:keywords? true}))
                           wrap-json-response) {:port port})
    (println (str "Running at localhost in port: " port))))
