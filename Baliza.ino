#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLEAdvertising.h>

BLEAdvertising *pAdvertising;

void setup() {
  Serial.begin(115200);

  BLEDevice::init("ESP32_CALLE72"); //Cambiar nombre del punto

  BLEServer *pServer = BLEDevice::createServer();
  pAdvertising = pServer->getAdvertising();

  // Crear mensaje
  std::string mensaje = "Llegando a porteria 72"; //Cambiar mensaje del punto

  // Crear objeto de datos del anuncio
  BLEAdvertisementData advertData;
  advertData.setServiceData(BLEUUID((uint16_t)0x1234), mensaje); // UUID de servicio personalizado

  pAdvertising->setAdvertisementData(advertData);

  // Iniciar publicidad BLE
  pAdvertising->start();
  Serial.println("Publicidad BLE iniciada...");
}

void loop() {
}

