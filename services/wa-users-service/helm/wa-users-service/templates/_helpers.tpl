{{- define "users.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "users.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "users.labels" -}}
app.kubernetes.io/name: {{ include "users.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
{{- define "users.secret" -}}
{{- if .Values.database.createSecret -}}
{{- printf "%s-db" (include "users.fullname" .) -}}
{{- else -}}
{{- required "database.existingSecret es obligatorio" .Values.database.existingSecret -}}
{{- end -}}
{{- end -}}
