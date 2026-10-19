{{- define "wa-users.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "wa-users.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "wa-users.labels" -}}
app.kubernetes.io/name: {{ include "wa-users.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
{{- define "wa-users.secret" -}}
{{- if .Values.database.createSecret -}}
{{- printf "%s-db" (include "wa-users.fullname" .) -}}
{{- else -}}
{{- required "database.existingSecret is required" .Values.database.existingSecret -}}
{{- end -}}
{{- end -}}
