# ==========================================================
#  WIREGUARD CLOUD TUNNEL FOR ROUTEROS v7: A Yeik Sitt Wifi
#  Connects router behind Starlink/NAT to Central Management VPS
# ==========================================================

:do {
  /interface wireguard add name=wg-cloud listen-port=13232 private-key="eEOn1K/yp8600PkNqJKMrxvZxxPBfmDDXUrdPKMczHk=" comment="Hotspot Cloud VPS Tunnel"
} on-error={
  /interface wireguard set [find name=wg-cloud] listen-port=13232 private-key="eEOn1K/yp8600PkNqJKMrxvZxxPBfmDDXUrdPKMczHk="
}

:do {
  /ip address add address=10.200.0.3/24 interface=wg-cloud comment="Cloud VPN Address"
} on-error={
  /ip address set [find interface=wg-cloud] address=10.200.0.3/24
}

:do {
  /interface wireguard peers add interface=wg-cloud endpoint-address="3.84.81.152" endpoint-port=51820 public-key="geEjgD9DIT4cbBHqR6vF5S9l2DDtyrZzQbon3bMQK2U=" allowed-address=10.200.0.0/24 persistent-keepalive=25s comment="VPS Server Endpoint"
} on-error={
  /interface wireguard peers set [find interface=wg-cloud] endpoint-address="3.84.81.152" endpoint-port=51820 public-key="geEjgD9DIT4cbBHqR6vF5S9l2DDtyrZzQbon3bMQK2U=" allowed-address=10.200.0.0/24 persistent-keepalive=25s
}

# Ensure API, SSH and FTP are enabled for remote control
:do { /ip service enable [find name="api"] } on-error={}
:do { /ip service set [find name="api"] port=8728 } on-error={}
:do { /ip service enable [find name="ssh"] } on-error={}
:do { /ip service set [find name="ssh"] port=22 } on-error={}
:do { /ip service enable [find name="ftp"] } on-error={}
:do { /ip service set [find name="ftp"] port=21 } on-error={}

# Allow remote management through WireGuard interface in firewall
:do {
  /ip firewall filter add chain=input in-interface=wg-cloud action=accept comment="Allow Cloud Remote Management" place-before=0
} on-error={{}}
:do {
  /ip firewall filter add chain=input src-address=10.200.0.0/24 action=accept comment="Allow Cloud WireGuard Subnet" place-before=0
} on-error={{}}

:put ">>> WireGuard Cloud Tunnel Configured! Router IP is 10.200.0.3 <<<"
:put ">>> Remote Cloud Relay: 3.84.81.152:8732 <<<"
