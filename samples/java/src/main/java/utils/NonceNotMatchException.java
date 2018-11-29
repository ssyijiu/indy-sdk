package utils;


class NonceNotMatchException extends Exception {
    public NonceNotMatchException() {
        super("nonces do not match");
    }
}
