package com.betpass.mc01pilot.airport.notam

object NotamRules {
    val abbreviations = linkedMapOf(
        "ACFT" to "aeronave", "AD" to "aeródromo", "APCH" to "aproximação", "APN" to "pátio", "AUTH" to "autorização",
        "CLSD" to "fechado", "CTC" to "contato", "CTR" to "zona de controle", "DLY" to "diariamente", "FREQ" to "frequência",
        "IFR" to "voo por instrumentos", "LDG" to "pouso", "NAVAID" to "auxílio à navegação", "O/R" to "sob solicitação",
        "PPR" to "autorização prévia requerida", "PSN" to "posição", "RWY" to "pista", "SID" to "saída padrão",
        "STAR" to "chegada padrão", "TMA" to "área terminal", "TKOF" to "decolagem", "TWY" to "taxiway", "U/S" to "fora de serviço", "VFR" to "voo visual"
    )
}
