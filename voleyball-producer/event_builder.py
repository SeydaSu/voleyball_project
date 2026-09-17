import json
import uuid
from datetime import datetime, timezone
from mac_state import MacDurumu
from config import MAC_ID


def skor_artis_event(durum: MacDurumu, takim: str) -> str:
    event = {
        "eventId": str(uuid.uuid4()),
        "macId": MAC_ID,
        "olayTipi": "SKOR_ARTIS",
        "takim": takim,
        "setNo": durum.set_no,
        "skorA": durum.skor_a,
        "skorB": durum.skor_b,
        "zaman": datetime.now(timezone.utc).isoformat()
    }
    return json.dumps(event)


def mac_bitti_event(durum: MacDurumu) -> str:
    event = {
        "eventId": str(uuid.uuid4()),
        "macId": MAC_ID,
        "olayTipi": "MAC_BITTI",
        "setNo": durum.set_no,
        "skorA": durum.skor_a,
        "skorB": durum.skor_b,
        "kazananTakim": durum.kazanan_takim,
        "zaman": datetime.now(timezone.utc).isoformat()
    }
    return json.dumps(event)