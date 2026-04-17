package config

import (
	"log"
	"os"
	"strings"

	"github.com/joho/godotenv"
)

type Config struct {
	Port          string
	PostgresURL   string
	RedisURL      string
	KafkaBrokers  []string
	JWTSecret     string
	SpringBootURL string
	PythonAIURL          string
	Environment          string
	InternalSharedSecret string
	AllowedOrigins       []string
	KafkaSecurityProtocol string
	KafkaSaslMechanism    string
	KafkaUsername         string
	KafkaPassword         string
}

func Load() *Config {
	// Load .env file if present (for local dev — ignored in Docker)
	if err := godotenv.Load(); err != nil {
		log.Println("ℹ️  No .env file found, using environment variables")
	}

	cfg := &Config{
		Port:          getEnv("PORT", "9090"),
		PostgresURL:   requireEnv("POSTGRES_URL"),
		RedisURL:      requireEnv("REDIS_URL"),
		KafkaBrokers:  strings.Split(requireEnv("KAFKA_BROKERS"), ","),
		JWTSecret:     requireEnv("JWT_SECRET"),
		SpringBootURL: getEnv("SPRING_BOOT_URL", "http://spring-boot:8080"),
		PythonAIURL:          getEnv("PYTHON_AI_URL", "http://python-ai:8000"),
		Environment:          getEnv("ENVIRONMENT", "development"),
		InternalSharedSecret: requireEnv("INTERNAL_SHARED_SECRET"),
		AllowedOrigins:       strings.Split(getEnv("ALLOWED_ORIGINS", "http://localhost"), ","),
		KafkaSecurityProtocol: getEnv("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT"),
		KafkaSaslMechanism:    getEnv("KAFKA_SASL_MECHANISM", "SCRAM-SHA-256"),
		KafkaUsername:         getEnv("KAFKA_USERNAME", ""),
		KafkaPassword:         getEnv("KAFKA_PASSWORD", ""),
	}

	log.Printf("✅ Config loaded (env=%s, port=%s, kafka=%v)", cfg.Environment, cfg.Port, cfg.KafkaBrokers)
	return cfg
}

// requireEnv reads a mandatory env var and panics if missing
func requireEnv(key string) string {
	val := os.Getenv(key)
	if val == "" {
		log.Fatalf("❌ Required environment variable %s is not set", key)
	}
	return val
}

func getEnv(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}
