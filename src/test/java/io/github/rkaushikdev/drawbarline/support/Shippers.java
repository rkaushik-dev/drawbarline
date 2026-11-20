package io.github.rkaushikdev.drawbarline.support;

import io.github.rkaushikdev.drawbarline.customers.CustomerRegister;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.customers.Shipper;

/** The two shippers on the small working railroad and the track each is served on. */
public final class Shippers {

    /** The paper mill at Escanaba, billed car by car. */
    public static final Shipper MILL = new Shipper("ESCPAP", "Escanaba Paper");

    /** The elevator at Powers, on an average agreement. */
    public static final Shipper ELEVATOR = new Shipper("PWRGRN", "Powers Grain", true);

    private Shippers() {}

    /** Both shippers, the mill house track of eleven spots and the elevator track of twenty. */
    public static CustomerRegister aCustomerRegister() {
        CustomerRegister register = new CustomerRegister();
        register.addShipper(MILL);
        register.addShipper(ELEVATOR);
        register.addIndustry(new Industry("ESCPAP1", "mill house track", "ESC", MILL, 11));
        register.addIndustry(new Industry("PWRGRN1", "elevator", "PWR", ELEVATOR, 20));
        return register;
    }
}
