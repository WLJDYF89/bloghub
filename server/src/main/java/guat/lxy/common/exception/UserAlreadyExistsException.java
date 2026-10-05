package guat.lxy.common.exception;

public class UserAlreadyExistsException extends BaseException {

    public UserAlreadyExistsException(){

    }

    public UserAlreadyExistsException(String msg){
        super(msg);
    }
}
