/*
 * blink.c:
 *	Standard "blink" program in wiringPi. Blinks an LED connected
 *	to the first GPIO pin.
 *
 * Copyright (c) 2012-2013 Gordon Henderson.
 ***********************************************************************
 * This file is part of wiringPi:
 *      https://github.com/WiringPi/WiringPi
 *
 *    wiringPi is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    wiringPi is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with wiringPi.  If not, see <http://www.gnu.org/licenses/>.
 ***********************************************************************
 */

#include <stdio.h>
#include <wiringPi.h>
#include <signal.h>

// LED Pin - wiringPi
//  pin 0 is BCM_GPIO 17
//  pin 1 is BCM_GPIO 18
//  pin 2 is BCM_GPIO 27
#define LED 2

static int terminate_process = 0;

static void Signal_handler(int sig);

int main (void)
{
  printf ("Raspberry Pi blink\n") ;

  if (wiringPiSetup () == -1)
    return 1 ;

  pinMode (LED, OUTPUT) ;

  // Set the handler for SIGTERM (15)
  signal(SIGTERM, Signal_handler);
  signal(SIGHUP,  Signal_handler);
  signal(SIGINT,  Signal_handler);
  signal(SIGQUIT, Signal_handler);
  signal(SIGTRAP, Signal_handler);
  signal(SIGABRT, Signal_handler);
  signal(SIGALRM, Signal_handler);
  signal(SIGUSR1, Signal_handler);
  signal(SIGUSR2, Signal_handler);

  while (!terminate_process)
  {
    digitalWrite (LED, HIGH) ;	// On
    delay (500) ;		// mS
    digitalWrite (LED, LOW) ;	// Off
    delay (500) ;
  }

  digitalWrite (LED, LOW) ; // Off

  return 0 ;
}

//**********************************************************************************************************************

/**
 * Intercepts and handles signals from QNX
 * This function is called when the SIGTERM signal is raised by QNX
 */
void Signal_handler(int sig)
{
  printf("Received signal %d\n", sig);

  // Signal process to exit.
  terminate_process = 1;
}

//**********************************************************************************************************************
