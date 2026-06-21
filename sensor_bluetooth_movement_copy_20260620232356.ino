#include <SoftwareSerial.h>

// --- Configuración de Pines ---
const int trigPin = 9;
const int echoPin = 8;
const int ledEmergencia = 7; // Pin actualizado para tu LED rojo externo

// Módulo Bluetooth HC-05 ("VERDOSO")
// RX del Arduino (Pin 10) se conecta al TX del Bluetooth
// TX del Arduino (Pin 11) se conecta al divisor de tensión hacia el RX del Bluetooth
SoftwareSerial miBluetooth(10, 11); 

// --- Variables Globales ---
long duration;
int distance;
bool paroActivo = false; // Estado del paro de emergencia

void setup() {
  Serial.begin(9600);
  miBluetooth.begin(9600);

  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);
  pinMode(ledEmergencia, OUTPUT);

  // Aseguramos que el LED inicie apagado
  digitalWrite(ledEmergencia, LOW);

  Serial.println("Sistema Tankr Listo. Esperando comandos...");
}

void loop() {
  // 1. Escuchar si hay comandos provenientes de la app de Android
  if (miBluetooth.available() > 0) {
    char comando = miBluetooth.read();
    
    // Si recibe '1', activa el paro de emergencia
    if (comando == '1') {
      paroActivo = true;
      digitalWrite(ledEmergencia, HIGH); // Enciende tu LED rojo externo
      Serial.println("¡PARO DE EMERGENCIA ACTIVADO!");
    }
    // Si recibe '0', desactiva el paro
    else if (comando == '0') {
      paroActivo = false;
      digitalWrite(ledEmergencia, LOW); // Apaga el LED
      Serial.println("Sistema restablecido.");
    }
  }

  // 2. Lógica de medición (Solo funciona si el paro NO está activo)
  if (!paroActivo) {
    digitalWrite(trigPin, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin, LOW);

    duration = pulseIn(echoPin, HIGH);
    distance = duration * 0.0343 / 2;

    Serial.print("Nivel de agua a: ");
    Serial.print(distance);
    Serial.println(" cm");

    miBluetooth.println(distance);
  }

  // Pausa de 1 segundo para no saturar la conexión
  delay(1000);
}