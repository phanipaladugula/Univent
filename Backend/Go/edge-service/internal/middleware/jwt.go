package middleware

import (
	"context"
	"encoding/hex"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

type contextKey string

const (
	UserIDKey   contextKey = "user_id"
	UserRoleKey contextKey = "user_role"
)

type JWTAuth struct {
	secret []byte
}

func NewJWTAuth(hexSecret string) *JWTAuth {
	secretBytes, err := hex.DecodeString(hexSecret)
	if err != nil {
		// Fallback: use the hex string as-is
		secretBytes = []byte(hexSecret)
	}
	return &JWTAuth{secret: secretBytes}
}

// RequireAuth enforces JWT authentication
func (j *JWTAuth) RequireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		claims, err := j.extractClaims(r)
		if err != nil {
			http.Error(w, fmt.Sprintf(`{"success":false,"error":"%s"}`, err.Error()), http.StatusUnauthorized)
			return
		}

		ctx := context.WithValue(r.Context(), UserIDKey, claims.UserID)
		ctx = context.WithValue(ctx, UserRoleKey, claims.Role)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// RequireAdmin enforces ADMIN role
func (j *JWTAuth) RequireAdmin(next http.Handler) http.Handler {
	return j.RequireAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		role := r.Context().Value(UserRoleKey).(string)
		if role != "ROLE_ADMIN" && role != "ADMIN" {
			http.Error(w, `{"success":false,"error":"Admin access required"}`, http.StatusForbidden)
			return
		}
		next.ServeHTTP(w, r)
	}))
}

// OptionalAuth parses JWT if present but does NOT block
func (j *JWTAuth) OptionalAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		claims, err := j.extractClaims(r)
		if err == nil && claims != nil {
			ctx := context.WithValue(r.Context(), UserIDKey, claims.UserID)
			ctx = context.WithValue(ctx, UserRoleKey, claims.Role)
			r = r.WithContext(ctx)
		}
		next.ServeHTTP(w, r)
	})
}

type JWTClaims struct {
	UserID uuid.UUID
	Role   string
	Email  string
}

func (j *JWTAuth) extractClaims(r *http.Request) (*JWTClaims, error) {
	authHeader := r.Header.Get("Authorization")
	if authHeader == "" {
		return nil, fmt.Errorf("missing Authorization header")
	}

	parts := strings.SplitN(authHeader, " ", 2)
	if len(parts) != 2 || parts[0] != "Bearer" {
		return nil, fmt.Errorf("invalid Authorization format")
	}

	tokenString := parts[1]

	token, err := jwt.Parse(tokenString, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return j.secret, nil
	})

	if err != nil {
		return nil, fmt.Errorf("invalid token: %w", err)
	}

	mapClaims, ok := token.Claims.(jwt.MapClaims)
	if !ok || !token.Valid {
		return nil, fmt.Errorf("invalid token claims")
	}

	// Check token type — must be "access"
	if tokenType, ok := mapClaims["type"].(string); ok && tokenType != "access" {
		return nil, fmt.Errorf("invalid token type")
	}

	// Check expiry
	if exp, ok := mapClaims["exp"].(float64); ok {
		if time.Unix(int64(exp), 0).Before(time.Now()) {
			return nil, fmt.Errorf("token expired")
		}
	}

	// Extract user ID (sub claim)
	sub, ok := mapClaims["sub"].(string)
	if !ok {
		return nil, fmt.Errorf("missing sub claim")
	}

	userID, err := uuid.Parse(sub)
	if err != nil {
		return nil, fmt.Errorf("invalid user ID in token")
	}

	role, _ := mapClaims["role"].(string)

	return &JWTClaims{
		UserID: userID,
		Role:   role,
	}, nil
}

// Helper to get user ID from context
func GetUserID(ctx context.Context) (uuid.UUID, bool) {
	id, ok := ctx.Value(UserIDKey).(uuid.UUID)
	return id, ok
}

func GetUserRole(ctx context.Context) string {
	role, _ := ctx.Value(UserRoleKey).(string)
	return role
}
