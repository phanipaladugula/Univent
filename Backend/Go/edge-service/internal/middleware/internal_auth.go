package middleware

import (
	"context"
	"net/http"
)


// InternalAuthMiddleware validates the X-Internal-Token header against a shared secret.
// This is used for inter-service communication security.
func InternalAuthMiddleware(sharedSecret string) func(next http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			token := r.Header.Get("X-Internal-Token")
			if token == "" || token != sharedSecret {
				// We don't log the token for security reasons, but we log the attempt.
				http.Error(w, "Forbidden: Invalid internal token", http.StatusForbidden)
				return
			}
			// Set context flag to allow bypassing JWT check
			ctx := context.WithValue(r.Context(), InternalRequestKey, true)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}
