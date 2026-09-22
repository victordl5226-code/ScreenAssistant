# Roadmap — IA Local per ScreenAssistant / J.A.R.V.I.S.

## Visió
Convertir ScreenAssistant en un asistent **100% funcional sense internet**, amb LLM local, NLP, STT i TTS.

---

## Fase 1: LLM Local Real (Prioritat ALTA)

### Opció A: MLC LLM (ja tenim codi)
- **Què**: Integrar `org.mlc:mlc-android` quan hi hagi release estable
- **Model**: Phi-3 Mini (2.3GB) o TinyLlama (1.1GB)
- **Status**: ⏳ Bloquejat per release estable
- **Acció**: Monitoritzar releases de MLC LLM

### Opció B: ONNX Runtime (alternativa)
- **Què**: Usar ONNX Runtime per Android amb models Optimum
- **Model**: DistilBERT, TinyLlama ONNX
- **Avantatge**: Més lleuger que MLC, sense dependències natives
- **Desavantatge**: Rendiment inferior a MLC

### Opció C: llama.cpp for Android
- **Què**: Port de llama.cpp a Android via JNI
- **Model**: Qualsevol model GGUF (TinyLlama, Phi-2, etc.)
- **Avantatge**: Comunitat activa, bons resultats
- **Desavantatge**: Compilació nativa complexa

### Recomanació: Opció C (llama.cpp)
```
core/ai/local/
├── LlamaCppEngine.kt          # Motor principal
├── LlamaModelDownloader.kt    # Descàrrega de models
├── LlamaConfig.kt             # Configuració (threads, context, etc.)
└── di/LlamaModule.kt          # DI
```

---

## Fase 2: NLP Local (Prioritat ALTA)

### Component: IntentClassifier
- **Què**: Classificar intencions del usuari sense núvol
- **Implementació**: Regex + keyword matching + small transformer
- **Intencions**: cridar, alarmar, nota, app, cerca, volum, etc.
- **Precisió objectiu**: >90% per a comandos habituals

### Component: EntityExtractor
- **Què**: Extreure entitats (noms, hores, números, app)
- **Implementació**: Regex + rules + NER petit
- **Entitats**: CONTACT, TIME, NUMBER, APP, LOCATION

### Arquitectura
```
core/nlp/
├── IntentClassifier.kt        # Classificació d'intencions
├── EntityExtractor.kt         # Extracció d'entitats
├── Intent.kt                  # Sealed class d'intencions
├── Entity.kt                  # Sealed class d'entitats
├── LocalNlpEngine.kt          # Orquestrador NLP
└── di/NlpModule.kt            # DI
```

---

## Fase 3: STT Local (Prioritat MITJANA)

### Opció: Whisper.cpp for Android
- **Què**: Whisper compilat per Android via JNI
- **Model**: whisper-tiny (75MB) o whisper-base (140MB)
- **Avantatge**: Alta precisió, multilingüe
- **Desavantatge**: Lent en CPU, necessita GPU

### Alternativa: Vosk
- **Què**: ASR offline lleuger
- **Model**: vosk-model-small-es (50MB)
- **Avantatge**: Lleuger, ràpid
- **Desavantatge**: Precisió inferior a Whisper

### Recomanació: Vosk (per mida) + Whisper (per precisió)
```
core/stt/
├── VoskSttEngine.kt          # ASR lleuger
├── WhisperSttEngine.kt       # ASR precís
├── SttManager.kt             # Selector automàtic
└── di/SttModule.kt           # DI
```

---

## Fase 4: TTS Local (Prioritat BAIXA)

### Opció: Coqui TTS
- **Què**: Text-to-Speech local amb models neural
- **Model**: tts_models/es_css10/vits (50MB)
- **Avantatge**: Veu natural, multilingüe
- **Desavantatge**: Lent en CPU

### Alternativa: Piper TTS
- **Què**: TTS lleuger i ràpid
- **Model**: ~20MB per idioma
- **Avantatge**: Ràpid, lleuger
- **Desavantatge**: Menys natural que Coqui

### Recomanació: Piper TTS (per rendiment)
```
core/tts/
├── PiperTtsEngine.kt         # TTS lleuger
├── CoquiTtsEngine.kt         # TTS natural
├── TtsManager.kt             # Selector
└── di/TtsModule.kt           # DI
```

---

## Fase 5: Millora FactExtractor (Prioritat MITJANA)

### Millores
1. **Multi-idioma**: Afegir anglès, portuguès, francès
2. **Context-aware**: Distingir "m'agrada X" de "no m'agrada X"
3. **Co-referència**: "el meu germà" → "Pere" → "ell"
4. **Actualització**: Actualitzar fets existents en lloc de duplicar
5. **Confiança dinàmica**: Augmentar confiança amb repetició

### Arquitectura
```
core/ai/memory/
├── FactExtractorV2.kt        # V2 amb multi-idioma
├── ContextResolver.kt        # Resol co-referències
├── FactUpdater.kt            # Actualitza fets existents
└── FactConfidence.kt         # Confiança dinàmica
```

---

## Fase 6: Personalització (Prioritat BAIXA)

### Component: UserPreferenceLearner
- **Què**: Aprendre preferències de l'usuari amb el temps
- **Dades**: Hores d'ús, comandos freqüents, respostes preferides
- **Implementació**: Counter-based + frequency analysis

### Component: AdaptivePersonality
- **Què**: Adaptar personalitat segons context
- **Paràmetres**: Formalitat, humor, brevetat
- **Implementació**: Rule-based + user feedback

---

## Pla d'implementació recomanat

### Sprint 1: NLP Local (2 setmanes)
1. IntentClassifier amb regex + keywords
2. EntityExtractor per entitats bàsiques
3. Integració amb OverlayViewModel
4. Tests unitaris

### Sprint 2: LLM Local (3 setmanes)
1. Integrar llama.cpp via JNI
2. Descàrrega de models (TinyLlama 1.1GB)
3. Inference loop amb streaming
4. Fallback a Gemini

### Sprint 3: STT Local (2 setmanes)
1. Integrar Vosk per ASR offline
2. Mic button amb Vosk
3. Fallback a Google Speech

### Sprint 4: Millora FactExtractor (1 setmana)
1. Multi-idioma (anglès)
2. Context-aware
3. FactUpdater

### Sprint 5: TTS Local (2 setmanes)
1. Integrar Piper TTS
2. Selector automàtic (local vs Android TTS)
3. Veus personalitzades

---

## Mètriques d'èxit

| Fase | Mètrica | Objectiu |
|------|---------|----------|
| NLP | Precisió intenció | >90% |
| NLP | Temps resposta | <100ms |
| LLM | Tokens/segon | >10 tokens/s |
| LLM | Memòria RAM | <2GB |
| STT | WER (Word Error Rate) | <15% |
| STT | Latència | <500ms |
| TTS | Qualitat MOS | >3.5/5 |
| TTS | Tokens/segon | >50 tokens/s |

---

## Riscos i mitigacions

| Risc | Impacte | Mitigació |
|------|---------|-----------|
| MLC sense release | Alt | Usar llama.cpp com a alternativa |
| Models massa grans | Alt | Usar models tiny/quantitzats (Q4) |
| CPU lenta | Mitjà | Optimitzar threads, usar GPU |
| Bateria | Mitjà | Limitar inferència, usar models petits |
| Memòria | Alt | Models Q4, alliberar després d'ús |

---

## Referències

- [llama.cpp](https://github.com/ggerganov/llama.cpp)
- [Vosk](https://alphacephei.com/vosk/)
- [Piper TTS](https://github.com/rhasspy/piper)
- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp)
- [ONNX Runtime](https://onnxruntime.ai/)
