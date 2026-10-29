# EasyMaps

App Android (Kotlin, Views/XML) de mobilidade e trânsito colaborativo.

## Configuração (obrigatória, uma vez por grupo)
1. Em https://console.firebase.google.com criar um projeto.
2. Adicionar uma app Android com o package `com.example.easymap` e descarregar o `google-services.json` para a pasta `app/`.
   (O ficheiro está no .gitignore; o grupo partilha-o por outro meio.)
3. Em *Authentication > Sign-in method* ativar **Email/palavra-passe**.
4. Em *Firestore Database* criar a base de dados e, para desenvolvimento, usar estas regras:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /occurrences/{id} { allow read, write: if request.auth != null; }
    match /users/{uid}/trips/{id} { allow read, write: if request.auth != null && request.auth.uid == uid; }
  }
}
```
5. Sync do Gradle e executar num dispositivo real (o GPS e os sensores não funcionam bem no emulador).

## Arquitetura
- `model/` classes de dados · `data/` SQLite local (AppDatabase), repositórios, Firestore, API Open-Meteo, SyncWorker
- `service/` TripService (GPS em foreground) + TripTracker (estado partilhado) · `sensors/` acelerómetro e luminosidade
- `ui/` ecrãs (auth, mapa, histórico, perfil, reportar) · `util/` formatação, imagens, notificações, vibração
- Dados: SQLite (local) + Firestore (remoto); o SyncWorker (WorkManager) envia os registos pendentes quando há rede.
