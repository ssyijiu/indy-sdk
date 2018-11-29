package utils;


class VerkeyNotMatchException extends Exception {
    public VerkeyNotMatchException() {
        super("verleys do not match");
    }
}
