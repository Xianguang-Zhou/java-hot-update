/*
 * Copyright (C) 2015 Xianguang Zhou <xianguang.zhou@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.zxg.hotupdate.test;

import java.util.Scanner;

/**
 *
 * @author Xianguang Zhou <xianguang.zhou@outlook.com>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        try (Scanner scanner = new Scanner(System.in)) {
            while (!"quit".equals(scanner.nextLine())) {
                Object obj = Main.class.getClassLoader()
                        .loadClass("org.zxg.hotupdate.test.Greeting").newInstance();
                obj.getClass().getMethod("hello").invoke(obj);
            }
        }
    }
}
