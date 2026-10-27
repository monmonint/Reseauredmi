package fr.reseauredmi.mode;

interface INetworkModeService {
    /**
     * Code réservé par Shizuku. Dans l'AIDL il vaut 16777114
     * (la transaction réelle côté serveur est 16777115).
     */
    void destroy() = 16777114;

    String apply(int subId, int slotIndex, int rilMode, long bitmask, String packageName) = 1;

    String read(int subId, int slotIndex, String packageName) = 2;
}
