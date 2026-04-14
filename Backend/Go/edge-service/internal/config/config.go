package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Port            string
	PostgresURL     string
	RedisURL        string
	KafkaBrokers    []string
	JWTSecret       string
	SpringBootURL   string
	PythonAIURL     string
	Environment     string
}

func Load() *Config {
	return &Config{
		Port:          getEnv("PORT", "9090"),
		PostgresURL:   getEnv("POSTGRES_URL", "postgres://postgres:univent_dev_pass@localhost:5432/univent?sslmode=disable"),
		RedisURL:      getEnv("REDIS_URL", "redis://localhost:6379/0"),
		KafkaBrokers:  strings.Split(getEnv("KAFKA_BROKERS", "localhost:9092"), ","),
		JWTSecret:     getEnv("JWT_SECRET", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"),
		SpringBootURL: getEnv("SPRING_BOOT_URL", "http://localhost:8080"),
		PythonAIURL:   getEnv("PYTHON_AI_URL", "http://localhost:8000"),
		Environment:   getEnv("ENVIRONMENT", "development"),
	}
}

func getEnv(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if val := os.Getenv(key); val != "" {
		if i, err := strconv.Atoi(val); err == nil {
			return i
		}
	}
	return fallback
}
