from flask import Flask, render_template, jsonify
from confluent_kafka import Producer
from config import KAFKA_BOOTSTRAP_SERVERS, KAFKA_TOPIC, MAC_ID
from mac_state import MacDurumu
from event_builder import skor_artis_event, mac_bitti_event

app = Flask(__name__)
producer = Producer({"bootstrap.servers": KAFKA_BOOTSTRAP_SERVERS})
durum = MacDurumu()


def delivery_report(err, msg):
    if err is not None:
        print(f"Mesaj gönderilemedi: {err}")
    else:
        print(f"Mesaj gönderildi -> {msg.topic()}")


@app.route("/")
def index():
    return render_template("index.html", durum=durum)


@app.route("/skor-artir/<takim>", methods=["POST"])
def skor_artir(takim):
    takim = takim.upper()
    if durum.bitti:
        return jsonify({"hata": "Maç zaten bitti"}), 400

    onceki_set = durum.set_no
    durum.skor_artir(takim)

    # skor artış event'i her zaman gönderilir
    event_json = skor_artis_event(durum, takim)
    producer.produce(KAFKA_TOPIC, key=MAC_ID, value=event_json, callback=delivery_report)

    # eğer bu artışla maç bittiyse, ayrıca MAC_BITTI event'i de gönderilir
    if durum.bitti:
        bitti_json = mac_bitti_event(durum)
        producer.produce(KAFKA_TOPIC, key=MAC_ID, value=bitti_json, callback=delivery_report)

    producer.poll(0)

    return jsonify({
        "setNo": durum.set_no,
        "skorA": durum.skor_a,
        "skorB": durum.skor_b,
        "kazanilanSetlerA": durum.kazanilan_setler_a,
        "kazanilanSetlerB": durum.kazanilan_setler_b,
        "bitti": durum.bitti,
        "kazananTakim": durum.kazanan_takim,
        "yeniSetBasladiMi": onceki_set != durum.set_no and not durum.bitti
    })


if __name__ == "__main__":
    app.run(debug=True, port=5000)