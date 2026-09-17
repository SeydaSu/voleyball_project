from dataclasses import dataclass, field


@dataclass
class MacDurumu:
    set_no: int = 1
    skor_a: int = 0
    skor_b: int = 0
    kazanilan_setler_a: int = 0
    kazanilan_setler_b: int = 0
    bitti: bool = False
    kazanan_takim: str | None = None
    set_bitti_event_gerekli: bool = False  # set biterse UI'a bilgi vermek için

    def _set_bitis_hedefi(self) -> int:
        return 15 if self.set_no == 5 else 25

    def _set_kazanildi_mi(self) -> str | None:
        hedef = self._set_bitis_hedefi()
        if self.skor_a >= hedef and self.skor_a - self.skor_b >= 2:
            return "A"
        if self.skor_b >= hedef and self.skor_b - self.skor_a >= 2:
            return "B"
        return None

    def skor_artir(self, takim: str) -> None:
        if self.bitti:
            raise ValueError("Maç zaten bitti, skor artırılamaz.")
        if takim == "A":
            self.skor_a += 1
        elif takim == "B":
            self.skor_b += 1
        else:
            raise ValueError("Takım 'A' ya da 'B' olmalı.")

        set_kazanan = self._set_kazanildi_mi()
        if set_kazanan:
            self._set_bitir(set_kazanan)

    def _set_bitir(self, set_kazanan: str) -> None:
        if set_kazanan == "A":
            self.kazanilan_setler_a += 1
        else:
            self.kazanilan_setler_b += 1

        if self.kazanilan_setler_a == 3 or self.kazanilan_setler_b == 3:
            self.bitti = True
            self.kazanan_takim = "A" if self.kazanilan_setler_a == 3 else "B"
        else:
            self.set_no += 1
            self.skor_a = 0
            self.skor_b = 0