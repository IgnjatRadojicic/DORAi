package dorai.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("EN", "English"), SPANISH("ES", "Español"), FRENCH("FR", "Français"), GERMAN("DE", "Deutsch");

    fun text(copy: UiCopy): String = when (this) {
        ENGLISH -> copy.english
        SPANISH -> copy.spanish
        FRENCH -> copy.french
        GERMAN -> copy.german
    }

    fun translateUi(english: String): String = catalog[english]?.let(::text) ?: english

    fun format(copy: UiCopy, vararg arguments: Pair<String, Any>): String {
        var value = text(copy)
        arguments.forEach { (name, argument) -> value = value.replace("{$name}", argument.toString()) }
        return value
    }

    companion object {
        private val catalog = UiCopy.entries.associateBy { it.english }
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

/** Translate interface copy only. Server data and citation text never pass through this function. */
@Composable
fun uiText(english: String): String = LocalAppLanguage.current.translateUi(english)

@Composable
fun uiText(copy: UiCopy): String = LocalAppLanguage.current.text(copy)

@Composable
fun uiFormat(copy: UiCopy, vararg arguments: Pair<String, Any>): String = LocalAppLanguage.current.format(copy, *arguments)

enum class UiCopy(val english: String, val spanish: String, val french: String, val german: String) {
    WORKSPACE("Workspace", "Espacio de trabajo", "Espace de travail", "Arbeitsbereich"),
    INVENTORY("Cloud inventory", "Inventario en la nube", "Inventaire cloud", "Cloud-Inventar"),
    AI_REVIEW("AI review", "Revisión de IA", "Revue IA", "KI-Prüfung"),
    FINDINGS_PAGE("Change findings", "Hallazgos de cambios", "Constats de changements", "Änderungsbefunde"),
    EXPORT_PAGE("Validation & export", "Validación y exportación", "Validation et export", "Validierung & Export"),
    CONNECTED("Connected", "Conectado", "Connecté", "Verbunden"),
    DEMO("Demo", "Demo", "Démo", "Demo"),
    PLAN("Terraform plan", "Plan de Terraform", "Plan Terraform", "Terraform-Plan"),
    PLAN_LIMIT("JSON / 10 MB", "JSON / 10 MB", "JSON / 10 Mo", "JSON / 10 MB"),
    SELECT_PLAN("Select a plan", "Seleccionar un plan", "Sélectionner un plan", "Plan auswählen"),
    BROWSE("Browse...", "Examinar...", "Parcourir...", "Durchsuchen..."),
    SAMPLE_PLAN("Sample plan", "Plan de ejemplo", "Plan d'exemple", "Beispielplan"),
    CHECK_PLAN("Check plan", "Comprobar plan", "Vérifier le plan", "Plan prüfen"),
    CHECKING("Checking...", "Comprobando...", "Vérification...", "Prüfung läuft..."),
    FINDINGS("Findings", "Hallazgos", "Constats", "Befunde"),
    NO_RESULTS("No results", "Sin resultados", "Aucun résultat", "Keine Ergebnisse"),
    NO_FINDINGS("No findings", "Sin hallazgos", "Aucun constat", "Keine Befunde"),
    NO_MATCHES("No matching findings", "No hay hallazgos coincidentes", "Aucun constat correspondant", "Keine passenden Befunde"),
    READ_ONLY("Read-only access", "Acceso de solo lectura", "Accès en lecture seule", "Schreibgeschützter Zugriff"),
    ALL_FINDINGS("All findings", "Todos los hallazgos", "Tous les constats", "Alle Befunde"),
    HIGH("High", "Alta", "Élevée", "Hoch"),
    MEDIUM("Medium", "Media", "Moyenne", "Mittel"),
    LOW("Low", "Baja", "Faible", "Niedrig"),
    ALL_SEVERITIES("All severities", "Todas las gravedades", "Toutes les sévérités", "Alle Schweregrade"),
    SEVERITY("Severity", "Gravedad", "Sévérité", "Schweregrad"),
    FINDING_EVIDENCE("Finding / Evidence", "Hallazgo / Evidencia", "Constat / Preuves", "Befund / Nachweise"),
    RESOURCE("Resource", "Recurso", "Ressource", "Ressource"),
    REGISTER("Register", "Registro", "Registre", "Register"),
    CRITICAL("CRITICAL", "CRÍTICA", "CRITIQUE", "KRITISCH"),
    CRITICAL_FUNCTION("Critical function", "Función crítica", "Fonction critique", "Kritische Funktion"),
    DETAILS("Details", "Detalles", "Détails", "Details"),
    LOCATION("Location", "Ubicación", "Localisation", "Standort"),
    NO_CITATION("No regulatory citation is available for this finding.", "No hay una cita normativa para este hallazgo.", "Aucune citation réglementaire pour ce constat.", "Für diesen Befund liegt kein regulatorisches Zitat vor."),
    REGISTER_VALIDATION("Register validation", "Validación del registro", "Validation du registre", "Registervalidierung"),
    REFRESH_VALIDATION("Refresh validation", "Actualizar validación", "Actualiser la validation", "Validierung aktualisieren"),
    VALIDATING("Validating...", "Validando...", "Validation...", "Validierung läuft..."),
    ERRORS("Errors", "Errores", "Erreurs", "Fehler"),
    WARNINGS("Warnings", "Advertencias", "Avertissements", "Warnungen"),
    TEMPLATES("Templates", "Plantillas", "Modèles", "Vorlagen"),
    VALID("Valid", "Válido", "Valide", "Gültig"),
    REVIEW_REQUIRED("Review required", "Revisión necesaria", "Vérification requise", "Prüfung erforderlich"),
    NO_VALIDATION("No validation report", "Sin informe de validación", "Aucun rapport de validation", "Kein Validierungsbericht"),
    NO_ISSUES("No issues", "Sin incidencias", "Aucune anomalie", "Keine Probleme"),
    STATUS("Status", "Estado", "Statut", "Status"),
    TEMPLATE_FIELD("Template / Field", "Plantilla / Campo", "Modèle / Champ", "Vorlage / Feld"),
    ISSUE("Issue", "Incidencia", "Anomalie", "Problem"),
    REFERENCE("Reference", "Referencia", "Référence", "Referenz"),
    ERROR("Error", "Error", "Erreur", "Fehler"),
    WARNING("Warning", "Advertencia", "Avertissement", "Warnung"),
    EXPORT("Export", "Exportación", "Export", "Export"),
    CSV_PACKAGE("Register CSV package", "Paquete CSV del registro", "Archive CSV du registre", "CSV-Registerpaket"),
    PDF_REPORT("Register report", "Informe del registro", "Rapport du registre", "Registerbericht"),
    ZIP_DRAFT("ZIP / DRAFT", "ZIP / BORRADOR", "ZIP / BROUILLON", "ZIP / ENTWURF"),
    PDF_DRAFT("PDF / DRAFT", "PDF / BORRADOR", "PDF / BROUILLON", "PDF / ENTWURF"),
    FINANCIAL_ENTITY("Financial entity", "Entidad financiera", "Entité financière", "Finanzunternehmen"),
    CONTRACTS("Contractual arrangements", "Acuerdos contractuales", "Accords contractuels", "Vertragliche Vereinbarungen"),
    ICT_SERVICES("ICT services", "Servicios TIC", "Services TIC", "IKT-Dienstleistungen"),
    ICT_PROVIDERS("ICT third-party providers", "Proveedores externos de TIC", "Prestataires tiers de TIC", "IKT-Drittdienstleister"),
    FUNCTIONS("Business functions", "Funciones de negocio", "Fonctions métier", "Geschäftsfunktionen"),
    COSTS("Costs & concentration", "Costes y concentración", "Coûts et concentration", "Kosten & Konzentration"),
    VALIDATION("Validation", "Validación", "Validation", "Validierung"),
    EVIDENCE("Sources & evidence", "Fuentes y evidencias", "Sources et preuves", "Quellen & Nachweise"),
    SAVE_CSV("Save CSV package", "Guardar paquete CSV", "Enregistrer l'archive CSV", "CSV-Paket speichern"),
    SAVE_PDF("Save PDF report", "Guardar informe PDF", "Enregistrer le rapport PDF", "PDF-Bericht speichern"),
    SAVING("Saving...", "Guardando...", "Enregistrement...", "Wird gespeichert..."),
    FILE_SAVED("File saved", "Archivo guardado", "Fichier enregistré", "Datei gespeichert"),
    SAVE_CANCELLED("Save cancelled. No new file was saved.", "Guardado cancelado.", "Enregistrement annulé.", "Speichern abgebrochen."),
    RETRY("Try again", "Reintentar", "Réessayer", "Erneut versuchen"),
    SCANNED("scanned", "escaneado", "analysé", "gescannt"),
    PLANNED("planned", "planificado", "planifié", "geplant"),
    AI_SUGGESTED("ai-suggested", "sugerido por IA", "suggéré par IA", "KI-Vorschlag"),
    HUMAN_CONFIRMED("human-confirmed", "confirmado", "confirmé", "bestätigt"),
    MINIMIZE("Minimize", "Minimizar", "Réduire", "Minimieren"),
    MAXIMIZE("Maximize", "Maximizar", "Agrandir", "Maximieren"),
    RESTORE_WINDOW("Restore window", "Restaurar ventana", "Restaurer la fenêtre", "Fenster wiederherstellen"),
    CLOSE("Close", "Cerrar", "Fermer", "Schließen"),
    LIGHT_ACTION("Switch to light theme", "Cambiar al tema claro", "Passer au thème clair", "Zum hellen Design wechseln"),
    DARK_ACTION("Switch to dark theme", "Cambiar al tema oscuro", "Passer au thème sombre", "Zum dunklen Design wechseln"),
    DARK_THEME("Dark theme", "Tema oscuro", "Thème sombre", "Dunkles Design"),
    LIGHT_THEME("Light theme", "Tema claro", "Thème clair", "Helles Design"),
    LANGUAGE("Language", "Idioma", "Langue", "Sprache"),
    REMOVE_PLAN("Remove plan", "Quitar plan", "Retirer le plan", "Plan entfernen"),
    EXPANDED("Expanded", "Expandido", "Développé", "Ausgeklappt"),
    COLLAPSED("Collapsed", "Contraído", "Réduit", "Eingeklappt"),
    OPEN_PLAN("Choose Terraform JSON", "Elegir JSON de Terraform", "Choisir un JSON Terraform", "Terraform-JSON auswählen"),
    SAVE_DOCUMENT("Save compliance document", "Guardar documento", "Enregistrer le document", "Dokument speichern"),
    CONFIRM_REPLACEMENT("Confirm replacement", "Confirmar sustitución", "Confirmer le remplacement", "Ersetzen bestätigen"),
    REPLACE_FILE("Replace {file}?", "¿Reemplazar {file}?", "Remplacer {file} ?", "{file} ersetzen?"),
    FINDING_COUNT_ONE("{shown} / {total} finding", "{shown} / {total} hallazgo", "{shown} / {total} constat", "{shown} / {total} Befund"),
    FINDING_COUNT_MANY("{shown} / {total} findings", "{shown} / {total} hallazgos", "{shown} / {total} constats", "{shown} / {total} Befunde"),
    ISSUE_COUNT_ONE("{count} issue", "{count} incidencia", "{count} anomalie", "{count} Problem"),
    ISSUE_COUNT_MANY("{count} issues", "{count} incidencias", "{count} anomalies", "{count} Probleme"),
    JSON_REQUIRED("Choose a JSON plan file.", "Elige un archivo JSON de plan.", "Choisissez un plan au format JSON.", "Wählen Sie eine JSON-Plandatei."),
    EMPTY_PLAN("The selected file is empty.", "El archivo está vacío.", "Le fichier est vide.", "Die ausgewählte Datei ist leer."),
    PLAN_TOO_LARGE("The plan must be no larger than 10 MB.", "El plan no debe superar 10 MB.", "Le plan ne doit pas dépasser 10 Mo.", "Der Plan darf nicht größer als 10 MB sein."),
    PLAN_OPEN_FAILED("The plan could not be opened.", "No se pudo abrir el plan.", "Impossible d'ouvrir le plan.", "Der Plan konnte nicht geöffnet werden."),
    FILE_OPEN_FAILED("The selected file could not be opened.", "No se pudo abrir el archivo.", "Impossible d'ouvrir le fichier.", "Die Datei konnte nicht geöffnet werden."),
    CHECK_FAILED("The plan check failed. Check the connection and try again.", "Falló la comprobación. Revisa la conexión e inténtalo de nuevo.", "La vérification a échoué. Vérifiez la connexion et réessayez.", "Die Prüfung ist fehlgeschlagen. Prüfen Sie die Verbindung und versuchen Sie es erneut."),
    VALIDATION_FAILED("Validation could not be loaded. Check the connection and retry.", "No se pudo cargar la validación. Revisa la conexión.", "Impossible de charger la validation. Vérifiez la connexion.", "Die Validierung konnte nicht geladen werden. Prüfen Sie die Verbindung."),
    EXPORT_DENIED("Your role does not allow exports.", "Tu rol no permite exportar.", "Votre rôle ne permet pas l'export.", "Ihre Rolle erlaubt keinen Export."),
    EXPORT_FAILED("The export could not be saved. Check the connection and destination, then retry.", "No se pudo guardar. Revisa la conexión y el destino.", "Impossible d'enregistrer. Vérifiez la connexion et la destination.", "Der Export konnte nicht gespeichert werden. Prüfen Sie Verbindung und Ziel."),
    WRONG_FILE_TYPE("The server returned an unexpected file type.", "El servidor devolvió un tipo de archivo inesperado.", "Le serveur a renvoyé un type de fichier inattendu.", "Der Server hat einen unerwarteten Dateityp zurückgegeben."),
    EMPTY_EXPORT("The server returned an empty file.", "El servidor devolvió un archivo vacío.", "Le serveur a renvoyé un fichier vide.", "Der Server hat eine leere Datei zurückgegeben."),
    INVALID_PDF("The downloaded file is not a valid PDF document.", "El archivo no es un PDF válido.", "Le fichier n'est pas un PDF valide.", "Die Datei ist kein gültiges PDF-Dokument."),
    INVALID_CSV("The downloaded file is not a valid CSV document.", "El archivo no es un paquete CSV válido.", "Le fichier n'est pas une archive CSV valide.", "Die Datei ist kein gültiges CSV-Paket."),
    DESTINATION_FAILED("The selected destination could not be opened.", "No se pudo abrir el destino.", "Impossible d'ouvrir la destination.", "Das Ziel konnte nicht geöffnet werden."),
    ACTIVITY_REQUIRED("A visible activity is required to choose a file.", "Abre la aplicación para elegir un archivo.", "Ouvrez l'application pour choisir un fichier.", "Öffnen Sie die Anwendung, um eine Datei auszuwählen."),
    DEMO_PLAN_ONLY("This plan is unavailable in demo mode.", "Este plan no está disponible en modo demo.", "Ce plan n'est pas disponible en mode démo.", "Dieser Plan ist im Demo-Modus nicht verfügbar."),
    EXPORT_UNAVAILABLE("Export unavailable.", "Exportación no disponible.", "Export indisponible.", "Export nicht verfügbar."),
    UNAVAILABLE("Unavailable", "No disponible", "Indisponible", "Nicht verfügbar"),
    SERVER_UNAVAILABLE("Server unavailable. Check the connection.", "Servidor no disponible. Revisa la conexión.", "Serveur indisponible. Vérifiez la connexion.", "Server nicht erreichbar. Prüfen Sie die Verbindung."),
    EXPORT_SIGN_IN("Sign in to export.", "Inicia sesión para exportar.", "Connectez-vous pour exporter.", "Melden Sie sich zum Export an."),
    EXPORT_VALIDATION("Resolve register validation errors before exporting.", "Resuelve los errores del registro antes de exportar.", "Corrigez les erreurs du registre avant l'export.", "Beheben Sie die Registerfehler vor dem Export."),
    REPLACE("Replace", "Reemplazar", "Remplacer", "Ersetzen"),
    CANCEL("Cancel", "Cancelar", "Annuler", "Abbrechen"),
    DEMO_LOCATION_TITLE("Data location outside the EU / EEA", "Ubicación de datos fuera de la UE / EEE", "Données hors UE / EEE", "Datenstandort außerhalb EU / EWR"),
    DEMO_LOCATION_DETAIL("Planned data location us-east-1 is outside the EU / EEA.", "La ubicación prevista us-east-1 está fuera de la UE / EEE.", "La localisation prévue us-east-1 est hors UE / EEE.", "Der geplante Datenstandort us-east-1 liegt außerhalb der EU / des EWR."),
    DEMO_RESILIENCE_TITLE("Critical function resilience review", "Revisión de resiliencia de función crítica", "Résilience d'une fonction critique", "Resilienzprüfung einer kritischen Funktion"),
    DEMO_RESILIENCE_DETAIL("The change affects critical function F1.", "El cambio afecta a la función crítica F1.", "Le changement affecte la fonction critique F1.", "Die Änderung betrifft die kritische Funktion F1."),
    DEMO_TAG_TITLE("Business function tag missing", "Falta la etiqueta de función de negocio", "Étiquette de fonction métier manquante", "Kennzeichnung der Geschäftsfunktion fehlt"),
    DEMO_TAG_DETAIL("The resource has no dora-function tag.", "El recurso no tiene etiqueta dora-function.", "La ressource n'a pas d'étiquette dora-function.", "Die Ressource hat kein dora-function-Tag."),
    DEMO_SERVICE_TITLE("Service type requires register review", "El tipo de servicio requiere revisión del registro", "Type de service à vérifier dans le registre", "Diensttyp erfordert Registerprüfung"),
    DEMO_SERVICE_DETAIL("The storage service type is not mapped to contract C2.", "El tipo de servicio de almacenamiento no está vinculado al contrato C2.", "Le type de service de stockage n'est pas lié au contrat C2.", "Der Speicherdiensttyp ist Vertrag C2 nicht zugeordnet."),
    DEMO_FUNCTION("Card payment processing", "Procesamiento de pagos con tarjeta", "Traitement des paiements par carte", "Verarbeitung von Kartenzahlungen"),
    DATA_LOCATION("Data location", "Ubicación de datos", "Localisation des données", "Datenstandort"),
    BUSINESS_FUNCTION("Business function", "Función de negocio", "Fonction métier", "Geschäftsfunktion"),
    DEMO_CONFIRM_LOCATION("Confirm the data location for the payments replica.", "Confirma la ubicación de datos de la réplica de pagos.", "Confirmez la localisation des données de la réplique de paiements.", "Bestätigen Sie den Datenstandort des Zahlungsreplikats."),
    DEMO_ASSIGN_FUNCTION("Assign a business function to the exports storage service.", "Asigna una función de negocio al servicio de almacenamiento de exportaciones.", "Associez une fonction métier au service de stockage des exports.", "Ordnen Sie dem Export-Speicherdienst eine Geschäftsfunktion zu."),
    DEMO_REVIEW_FUNCTION("Review the critical function mapping before preparing the final register.", "Revisa la asignación de la función crítica antes de preparar el registro final.", "Vérifiez l'association de la fonction critique avant de finaliser le registre.", "Prüfen Sie die Zuordnung der kritischen Funktion vor dem endgültigen Register.")
}
